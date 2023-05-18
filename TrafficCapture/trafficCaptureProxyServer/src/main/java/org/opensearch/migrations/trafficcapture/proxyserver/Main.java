package org.opensearch.migrations.trafficcapture.proxyserver;

import com.google.protobuf.CodedOutputStream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.logging.log4j.core.util.NullOutputStream;
import org.opensearch.common.settings.Settings;
import org.opensearch.migrations.trafficcapture.FileConnectionCaptureFactory;
import org.opensearch.migrations.trafficcapture.IChannelConnectionCaptureSerializer;
import org.opensearch.migrations.trafficcapture.IConnectionCaptureFactory;
import org.opensearch.migrations.trafficcapture.StreamChannelConnectionCaptureSerializer;
import org.opensearch.migrations.trafficcapture.kafkaoffloader.KafkaCaptureFactory;
import org.opensearch.migrations.trafficcapture.proxyserver.netty.NettyScanningHttpProxy;
import org.opensearch.security.ssl.DefaultSecurityKeyStore;
import org.opensearch.security.ssl.util.SSLConfigConstants;


import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import javax.net.ssl.SSLEngine;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
public class Main {

    static class Parameters {
        @Parameter(required = false,
                names = {"-p", "--backside-port"},
                //arity = 1,
                description = "Backside port number")
        int backsidePort = 80;
        @Parameter(required = false,
                names = {"-h", "--backside-host"},
                description = "Backside hostname")
        String backsideHostname = "webserver";
        @Parameter(required = false,
                names = {"-b", "--bind-port"},
                description = "port number to bind to")
        int bindPort = 9200;

        @Parameter(required = false,
                names = {"-c", "--config"},
                description = "yaml config file name")
        String configFile = null;

        @Parameter(required = false,
                names = {"-w", "--workdir"},
                description = "working directory")
        String workingDir = null;

        @Parameter(required = false,
                names = {"-k", "--kafka-prop"},
                description = "kafka props path")
        String kafkaPath = null;
    }

    public static Parameters parseArgs(String[] args) {
        Parameters p = new Parameters();
        JCommander jCommander = new JCommander(p);
        try {
            jCommander.parse(args);
            return p;
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            System.err.println("Got args: "+ String.join("; ", args));
            jCommander.usage();
            return null;
        }
    }


    private final static String HTTPS_CONFIG_PREFIX = "plugins.security.ssl.http.";

    @SneakyThrows
    private static Settings getSettings(@NonNull String configFile) {
        var builder = Settings.builder();
        try (var lines = Files.lines(Paths.get(configFile))) {
            lines
                    .map(line->Optional.of(line.indexOf('#')).filter(i->i>=0).map(i->line.substring(0, i)).orElse(line))
                    .filter(line->line.startsWith(HTTPS_CONFIG_PREFIX) && line.contains(":"))
                    .forEach(line->{
                        var parts = line.split(": *", 2);
                        builder.put(parts[0], parts[1]);
                    });
        }
        builder.put(SSLConfigConstants.SECURITY_SSL_TRANSPORT_ENABLED, false);
        builder.put("path.home", configFile);
        return builder.build();
    }

    private static IConnectionCaptureFactory getTraceConnectionCaptureFactory(String traceLogsDirectory) {
        if (traceLogsDirectory == null) {
            System.err.println("No trace log directory specified.  Logging to /dev/null");
            return new IConnectionCaptureFactory() {
                @Override
                public IChannelConnectionCaptureSerializer createOffloader(String connectionId) throws IOException {
                    return new StreamChannelConnectionCaptureSerializer(connectionId, () ->
                            CodedOutputStream.newInstance(NullOutputStream.getInstance()),
                            cos -> CompletableFuture.completedFuture(null));
                }
            };
        } else {
            return new FileConnectionCaptureFactory(traceLogsDirectory, 1024 * 1024 * 1024);
        }
    }

    private static IConnectionCaptureFactory getKafkaConnectionFactory(String kafkaPropsPath, int bufferSize) throws IOException {
        Properties producerProps = new Properties();
        try {
            producerProps.load(new FileReader(kafkaPropsPath));
        } catch (IOException e) {
            log.error("Unable to locate provided Kafka producer properties file path: " + kafkaPropsPath);
            throw e;
        }
        return new KafkaCaptureFactory(new KafkaProducer<>(producerProps), bufferSize);
    }

    private static IConnectionCaptureFactory getConnectionCaptureFactory(String kafkaPropsPath, int bufferSize) throws IOException {
        return new FileConnectionCaptureFactory("./traceLogs", 1024 * 1024 * 1024);
        //return getKafkaConnectionFactory(kafkaPropsPath, bufferSize);
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        var params = parseArgs(args);
        String kafkaPropsPath = params.kafkaPath;
        String traceLogsDirectory = null;
        // This should be added to the argument parser when added in
        int bufferSize = 1024 * 1024;
        var sksOp = Optional.ofNullable(params.configFile)
                .map(o->new DefaultSecurityKeyStore(getSettings(o),
                        Optional.ofNullable(params.workingDir).map(w-> Paths.get(w)).orElse(null)
                ));

        sksOp.ifPresent(x->x.initHttpSSLConfig());
        var proxy = new NettyScanningHttpProxy(params.bindPort);

        try {
            proxy.start(params.backsideHostname, params.backsidePort,
                    sksOp.map(sks-> (Supplier<SSLEngine>) () -> {
                        try {
                            var sslEngine = sks.createHTTPSSLEngine();
                            return sslEngine;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    //}).orElse(null), getTraceConnectionCaptureFactory(traceLogsDirectory));
                    }).orElse(null), getConnectionCaptureFactory(kafkaPropsPath, bufferSize));
        } catch (Exception e) {
            System.err.println(e);
            e.printStackTrace();
            throw e;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.err.println("Received shutdown signal.  Trying to shutdown cleanly");
                proxy.stop();
                System.err.println("Done stopping the proxy.");
            } catch (InterruptedException e) {
                System.err.println("Caught exception while shutting down: "+e);
                throw new RuntimeException(e);
            }
        }));
        // This loop just gives the main() function something to do while the netty event loops
        // work in the background.
        while (true) {
            // TODO: The kill signal will cause the sleep to throw an InterruptedException,
            // which may not be what we want to do - it seems like returning an exit code of 0
            // might make more sense.  This is something to research - e.g. by seeing if there's
            // specific behavior that POSIX recommends/requires.
            Thread.sleep(60*1000);
        }
    }
}
