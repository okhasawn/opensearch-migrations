package org.opensearch.migrations.replay.datahandlers.http;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.LastHttpContent;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.migrations.replay.AggregatedRawResponse;
import org.opensearch.migrations.replay.AggregatedTransformedResponse;
import org.opensearch.migrations.replay.datahandlers.IPacketFinalizingConsumer;
import org.opensearch.migrations.replay.util.DiagnosticTrackableCompletableFuture;
import org.opensearch.migrations.replay.util.StringTrackableCompletableFuture;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for sending the ByteBufs to the downstream packet receiver,
 * which in many cases will be the thing that sends the request over the network.
 *
 * Most of the logic within this class is to convert between ChannelFutures (netty's
 * futures) and CompletableFutures (Java's construct that came after).
 */
@Slf4j
public class NettySendByteBufsToPacketHandlerHandler extends ChannelInboundHandlerAdapter {
    final IPacketFinalizingConsumer<AggregatedRawResponse> packetReceiver;
    // final Boolean value indicates if the handler received a LastHttpContent message
    // TODO - make this threadsafe.  calls may come in on different threads
    DiagnosticTrackableCompletableFuture<String, Boolean> currentFuture;
    private AtomicReference<DiagnosticTrackableCompletableFuture<String, AggregatedTransformedResponse>>
            packetReceiverCompletionFutureRef;
    String diagnosticLabel;

    public NettySendByteBufsToPacketHandlerHandler(IPacketFinalizingConsumer packetReceiver, String diagnosticLabel) {
        this.packetReceiver = packetReceiver;
        this.packetReceiverCompletionFutureRef = new AtomicReference<>();
        this.diagnosticLabel = diagnosticLabel;
        currentFuture = DiagnosticTrackableCompletableFuture.factory.completedFuture(null,
                ()->"currentFuture for NettySendByteBufsToPacketHandlerHandler initialized to the base case for " + diagnosticLabel);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        log.debug("Handler removed for context " + ctx + " hash=" + System.identityHashCode(ctx));
        log.trace("HR: old currentFuture="+currentFuture);
//                        () -> "Waiting for NettySendByteBufsToPacketHandlerHandler to complete this future.  " +
//                                "this.currentFuture, whose completion will complete this future is currently:\n" +
//                                "    <<" + currentFuture + ">>\n");
        if (currentFuture.future.isDone()) {
            if (currentFuture.future.isCompletedExceptionally()) {
                packetReceiverCompletionFutureRef.set(currentFuture.getDeferredFutureThroughHandle((v,t)->
                                StringTrackableCompletableFuture.failedFuture(t, ()->"fixed failure"),
                        ()->"handlerRemoved: packetReceiverCompletionFuture receiving exceptional value"));
                return;
            } else if (currentFuture.get() == null) {
                log.info("The handler responsible for writing data to the server was detached before writing byte " +
                        "bytes.  Throwing a NoContentException so that the calling context can handle appropriately.");
                packetReceiverCompletionFutureRef.set(
                        StringTrackableCompletableFuture.failedFuture(new NoContentException(),
                                ()->"Setting NoContentException to the exposed CompletableFuture" +
                                        " of NettySendByteBufsToPacketHandlerHandler"));
                return;
            }
            // fall-through
        }

        var packetReceiverCompletionFuture = currentFuture.getDeferredFutureThroughHandle((v1, t1) -> {
                    assert v1 != null :
                            "expected in progress Boolean to be not null since null should signal that work was never started";
                    var transformationStatus = v1.booleanValue() ?
                            AggregatedTransformedResponse.HttpRequestTransformationStatus.COMPLETED :
                            AggregatedTransformedResponse.HttpRequestTransformationStatus.ERROR;
                    return packetReceiver.finalizeRequest().getDeferredFutureThroughHandle((v2, t2) -> {
                                if (t1 != null) {
                                    return StringTrackableCompletableFuture.<AggregatedTransformedResponse>failedFuture(t1,
                                            ()->"fixed failure from currentFuture.getDeferredFutureThroughHandle()");
                                } else if (t2 != null) {
                                    return StringTrackableCompletableFuture.<AggregatedTransformedResponse>failedFuture(t2,
                                            ()->"fixed failure from packetReceiver.finalizeRequest()");
                                } else {
                                    return StringTrackableCompletableFuture.completedFuture(Optional.ofNullable(v2)
                                                    .map(r->new AggregatedTransformedResponse(r,  transformationStatus))
                                                    .orElse(null),
                                            ()->"fixed value from packetReceiver.finalizeRequest()"
                                            );
                                }
                            },
                            ()->"handlerRemoved: NettySendByteBufsToPacketHandlerHandler is setting the completed value for its " +
                                    "packetReceiverCompletionFuture, after the packets have been finalized " +
                                    "to the packetReceiver");
                },
                () -> "handlerRemoved: waiting for the currentFuture to finish");
        currentFuture = packetReceiverCompletionFuture.getDeferredFutureThroughHandle((v,t)->
                StringTrackableCompletableFuture.<Boolean>completedFuture(true,
                        ()->"ignoring return type of packetReceiver.finalizeRequest() but waiting for it to finish"),
                ()->"Waiting for packetReceiver.finalizeRequest() and will return once that is done");
        packetReceiverCompletionFutureRef.set(packetReceiverCompletionFuture);
        log.trace("HR: new currentFuture="+currentFuture);
        super.handlerRemoved(ctx);
    }

    public DiagnosticTrackableCompletableFuture<String,AggregatedTransformedResponse> getPacketReceiverCompletionFuture() {
        assert packetReceiverCompletionFutureRef.get() != null :
                "expected close() to have removed the handler and for this to be non-null";
        return packetReceiverCompletionFutureRef.get();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            log.trace("read the following message and sending it to consumeBytes: " + msg +
                    " hashCode=" + System.identityHashCode(msg) +
                    " ctx hash=" + System.identityHashCode(ctx));
            var bb = ((ByteBuf) msg).retain();
            log.trace("CR: old currentFuture="+currentFuture);
            // I don't want to capture this object, but the preexisting value of the currentFuture field
            final var preexistingFutureForCapture = currentFuture;
            var numBytesToSend = bb.readableBytes();
            currentFuture = currentFuture.thenCompose(v-> {
                log.trace("chaining consumingBytes with " + msg + " lastFuture=" + preexistingFutureForCapture);
                var rval = packetReceiver.consumeBytes(bb);
                log.error("packetReceiver.consumeBytes()="+rval);
                bb.release();
                return rval.map(cf->cf.thenApply(ignore->false),
                        ()->"this NettySendByteBufsToPacketHandlerHandler.channelRead()'s future is going to return a" +
                                " completedValue of false to indicate that more packets may need to be sent");
            },
                    ()->"NettySendByteBufsToPacketHandlerHandler.channelRead waits for the previous future " +
                            "to finish before writing the next set of " + numBytesToSend + " bytes ");
            log.trace("CR: new currentFuture="+currentFuture);
        } else if (msg instanceof LastHttpContent) {
            currentFuture = currentFuture.map(cf->cf.thenApply(ignore->true),
                    ()->"this NettySendByteBufsToPacketHandlerHandler.channelRead()'s future is prepared to return a " +
                            "completedValue of true since the LastHttpContent object has been received");
        } else {
            ctx.fireChannelRead(msg);
        }
    }

}
