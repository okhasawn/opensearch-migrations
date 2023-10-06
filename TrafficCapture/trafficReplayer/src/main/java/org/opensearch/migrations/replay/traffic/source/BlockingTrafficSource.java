package org.opensearch.migrations.replay.traffic.source;

import com.google.protobuf.Timestamp;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.migrations.replay.datatypes.ITrafficStreamKey;
import org.opensearch.migrations.replay.Utils;
import org.opensearch.migrations.trafficcapture.protos.TrafficStream;
import org.opensearch.migrations.trafficcapture.protos.TrafficStreamUtils;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * The BlockingTrafficSource class implements ITrafficCaptureSource and wraps another instance.
 * It keeps track of a couple Instants for the last timestamp from a TrafficStreamObservation
 * and for a high-watermark (stopReadingAt) that has been supplied externally.  If the last
 * timestamp was PAST the high-watermark, calls to read the next chunk (readNextTrafficStreamChunk)
 * will return a CompletableFuture that is blocking and won't be released until
 * somebody advances the high-watermark by calling stopReadsPast, which takes in a
 * point-in-time (in System time) and adds some buffer to it.
 *
 * This class is designed to only be threadsafe for any number of callers to call stopReadsPast
 * and independently for one caller to call readNextTrafficStreamChunk() and to wait for the result
 * to complete before another caller calls it again.
 */
@Slf4j
public class BlockingTrafficSource implements ITrafficCaptureSource, BufferedFlowController {

    @ToString
    static class OutstandingInfo {
        AtomicInteger countRemaining;
        int cost;

        public OutstandingInfo(int expectedSignalsToReceive, int cost) {
            this.countRemaining = new AtomicInteger(expectedSignalsToReceive);
            this.cost = cost;
        }
    }

    @EqualsAndHashCode
    @ToString
    static class TrafficStreamOutstandingInfoKey {
        String connectionId;
        int index;
        public TrafficStreamOutstandingInfoKey(ITrafficStreamKey k) {
            connectionId = k.getConnectionId();
            index = k.getTrafficStreamIndex();
        }
    }

    private final ISimpleTrafficCaptureSource underlyingSource;
    private final AtomicReference<Instant> lastTimestampSecondsRef;
    private final AtomicReference<Instant> stopReadingAtRef;
    /**
     * Limit the number of readers to one at a time and only if we haven't yet maxed out our time buffer
     */
    private final Semaphore readGate;
    private final Semaphore liveTrafficStreamCostGate;
    private final ToIntFunction<TrafficStream> computeExpectedSignalsToReceive;
    private final ToIntFunction<TrafficStream> computeSemaphoreCost;
    private final ConcurrentHashMap<TrafficStreamOutstandingInfoKey, OutstandingInfo> outstandingTrafficStreamInfoMap;

    private final Duration bufferTimeWindow;

    public BlockingTrafficSource(ISimpleTrafficCaptureSource underlying,
                                 Duration bufferTimeWindow,
                                 int maxConcurrentCost,
                                 ToIntFunction<TrafficStream> computeExpectedSignalsToReceive,
                                 ToIntFunction<TrafficStream> computeSemaphoreCost) {
        this.underlyingSource = underlying;
        this.stopReadingAtRef = new AtomicReference<>(Instant.EPOCH);
        this.lastTimestampSecondsRef = new AtomicReference<>(Instant.EPOCH);
        this.bufferTimeWindow = bufferTimeWindow;
        this.readGate = new Semaphore(0);
        this.liveTrafficStreamCostGate = new Semaphore(maxConcurrentCost);
        this.outstandingTrafficStreamInfoMap = new ConcurrentHashMap<>();
        this.computeExpectedSignalsToReceive = computeExpectedSignalsToReceive;
        this.computeSemaphoreCost = computeSemaphoreCost;
    }

    /**
     * This will move the current high-watermark on reads that we can do to the specified time PLUS the
     * bufferTimeWindow (which was set in the c'tor)
     * @param pointInTime
     */
    @Override
    public void stopReadsPast(Instant pointInTime) {
        var prospectiveBarrier = pointInTime.plus(bufferTimeWindow);
        var newValue = Utils.setIfLater(stopReadingAtRef, prospectiveBarrier);
        if (newValue.equals(prospectiveBarrier)) {
            log.atLevel(readGate.hasQueuedThreads() ? Level.INFO: Level.TRACE)
                    .setMessage(() -> "Releasing the block on readNextTrafficStreamChunk and set" +
                            " the new stopReadingAtRef=" + newValue).log();
            // No reason to signal more than one reader.  We don't support concurrent reads with the current contract
            readGate.drainPermits();
            readGate.release();
        } else {
            log.atTrace()
                    .setMessage(() -> "stopReadsPast: " + pointInTime + " [buffer=" + prospectiveBarrier +
                            "] didn't move the cursor because the value was already at " + newValue
                    ).log();
        }
    }

    @Override
    public void doneProcessing(ITrafficStreamKey key) {
        var k = new TrafficStreamOutstandingInfoKey(key);
        var existingOutstandingInfo = outstandingTrafficStreamInfoMap.get(k);
        log.atDebug().setMessage(()->"existingOutstandingInfo for " + k + " = " + existingOutstandingInfo).log();
        var newCount = existingOutstandingInfo.countRemaining.decrementAndGet();
        if (newCount == 0) {
            liveTrafficStreamCostGate.release(existingOutstandingInfo.cost);
            outstandingTrafficStreamInfoMap.remove(k);
            log.atDebug().setMessage(()->"released "+existingOutstandingInfo.cost+
                    " liveTrafficStreamCostGate.availablePermits="+liveTrafficStreamCostGate.availablePermits()+
                    "outstandingTrafficStreamInfoMap.size="+outstandingTrafficStreamInfoMap.size()).log();
        }
    }

    public Duration getBufferTimeWindow() {
        return bufferTimeWindow;
    }

    /**
     * Reads the next chunk that is available before the current stopReading barrier.  However,
     * that barrier isn't meant to be a tight barrier with immediate effect.
     *
     * @return
     */
    @Override
    public CompletableFuture<List<ITrafficStreamWithKey>>
    readNextTrafficStreamChunk() {
        var trafficStreamListFuture =
                CompletableFuture.supplyAsync(() -> {
                                    if (stopReadingAtRef.get().equals(Instant.EPOCH)) { return null; }
                                    while (stopReadingAtRef.get().isBefore(lastTimestampSecondsRef.get())) {
                                        try {
                                            log.atInfo().setMessage(
                                                            "blocking until signaled to read the next chunk last={} stop={}")
                                                    .addArgument(lastTimestampSecondsRef.get())
                                                    .addArgument(stopReadingAtRef.get())
                                                    .log();
                                            readGate.acquire();
                                        } catch (InterruptedException e) {
                                            log.atWarn().setCause(e).log("Interrupted while waiting to read more data");
                                            throw new RuntimeException(e);
                                        }
                                    }
                                    return null;
                                },
                                task -> new Thread(task).start())
                        .thenCompose(v->
                            // We already look ahead in time.  In this case, we don't know what the cost will be of
                            // the next chunk until we get the chunk.  So, don't bother checking the semaphore until
                            // we have the chunk
                            underlyingSource.readNextTrafficStreamChunk()
                                        .whenComplete(this::adjustOutstandingWorkGivenResponse));
        return trafficStreamListFuture.whenComplete((v,t)->{
            if (t != null) {
                return;
            }
            var maxLocallyObservedTimestamp = v.stream().flatMap(tswk->tswk.getStream().getSubStreamList().stream())
                    .map(tso->tso.getTs())
                    .max(Comparator.comparingLong(Timestamp::getSeconds)
                            .thenComparingInt(Timestamp::getNanos))
                    .map(TrafficStreamUtils::instantFromProtoTimestamp)
                    .orElse(Instant.EPOCH);
            Utils.setIfLater(lastTimestampSecondsRef, maxLocallyObservedTimestamp);
            log.atTrace().setMessage(()->"end of readNextTrafficStreamChunk trigger...lastTimestampSecondsRef="
                    +lastTimestampSecondsRef.get()).log();
        });
    }

    private void adjustOutstandingWorkGivenResponse(List<ITrafficStreamWithKey> trafficStreamWithKeys, Throwable t) {
        if (trafficStreamWithKeys == null) {
            return;
        }
        var totalCost = trafficStreamWithKeys.stream()
                .mapToInt(tswk -> {
                    var cost = computeSemaphoreCost.applyAsInt(tswk.getStream());
                    var oldVal = outstandingTrafficStreamInfoMap.put(new TrafficStreamOutstandingInfoKey(tswk.getKey()),
                            new OutstandingInfo(computeExpectedSignalsToReceive.applyAsInt(tswk.getStream()), cost));
                    assert oldVal == null;
                    return cost;
                }).sum();
        log.atDebug().setMessage(()->"liveTrafficStreamCostGate.permits: {} acquiring: {} costDistribution: {}")
                .addArgument(liveTrafficStreamCostGate.availablePermits())
                .addArgument(totalCost)
                .addArgument(()->trafficStreamWithKeys.stream().map(tswk->{
                            var k = new TrafficStreamOutstandingInfoKey(tswk.getKey());
                            var info = outstandingTrafficStreamInfoMap.get(k);
                            return tswk + ": " + info.cost + " (" + info.countRemaining + ")";
                        })
                        .collect(Collectors.joining(",")))
                .log();
        try {
            liveTrafficStreamCostGate.acquire(totalCost);
            log.atDebug().setMessage(()->"Acquired liveTrafficStreamCostGate (available=" +
                    liveTrafficStreamCostGate.availablePermits()+")").log();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        underlyingSource.close();
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", BlockingTrafficSource.class.getSimpleName() + "[", "]")
                .add("bufferTimeWindow=" + bufferTimeWindow)
                .add("lastTimestampSecondsRef=" + lastTimestampSecondsRef)
                .add("stopReadingAtRef=" + stopReadingAtRef)
                .add("readGate=" + readGate)
                .toString();
    }
}
