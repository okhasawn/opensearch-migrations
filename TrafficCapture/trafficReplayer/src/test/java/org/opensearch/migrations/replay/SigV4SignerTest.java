package org.opensearch.migrations.replay;

import io.netty.buffer.Unpooled;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.HttpHeaders;
import javax.net.ssl.SSLException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Base64;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import java.nio.charset.Charset;
import io.netty.buffer.ByteBuf;
import org.opensearch.migrations.replay.datahandlers.NettyPacketToHttpConsumer;
import org.opensearch.migrations.replay.datahandlers.http.HttpJsonMessageWithFaultingPayload;
import org.opensearch.migrations.replay.datahandlers.http.ListKeyAdaptingCaseInsensitiveHeadersMap;
import org.opensearch.migrations.replay.datahandlers.http.StrictCaseInsensitiveHttpHeadersMap;
import org.opensearch.migrations.testutils.SimpleHttpResponse;
import org.opensearch.migrations.testutils.SimpleHttpClientForTesting;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import java.util.Map;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.apache.hc.client5.http.impl.classic.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SigV4SignerTest {

    public static final String HOSTNAME_STRING = "6k6etwtuh12fwuey0h8h.us-east-1.aoss.amazonaws.com";
    private SigV4Signer signer;
    private static final String service = "aoss";
    private static final String region = "us-east-1";

    private SimpleHttpClientForTesting client;

    @SneakyThrows
    @BeforeEach
    void setup() {
        client = new SimpleHttpClientForTesting(false);
        HttpJsonMessageWithFaultingPayload msg = new HttpJsonMessageWithFaultingPayload();
        msg.setMethod("PUT");
        msg.setUri("/ok92worked");
        msg.setProtocol("https");
        var key_id = System.getenv("AWS_ACCESS_KEY_ID");
        var secret_key = System.getenv("AWS_SECRET_ACCESS_KEY");
        var token = System.getenv("AWS_SESSION_TOKEN");
        System.setProperty("aws.accessKeyId", key_id);
        System.setProperty("aws.secretAccessKey", secret_key);
        System.setProperty("aws.sessionToken", token);

        signer = new SigV4Signer(msg, DefaultCredentialsProvider.create());
    }

    private static SslContext loadSslContext(URI serverUri, boolean allowInsecureConnections) throws SSLException {
        if (serverUri.getScheme().toLowerCase().equals("https")) {
            var sslContextBuilder = SslContextBuilder.forClient();
            if (allowInsecureConnections) {
                sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            }
            return sslContextBuilder.build();
        } else {
            return null;
        }
    }

    @SneakyThrows
    @Test
    void testEmptyBodySignedRequestTestPut() {
/*
        URI myURI = new URI("https://6k6etwtuh12fwuey0h8h.us-east-1.aoss.amazonaws.com:443");

        var nettyHandler = new NettyPacketToHttpConsumer(new NioEventLoopGroup(), new URI("https://6k6etwtuh12fwuey0h8h.us-east-1.aoss.amazonaws.com:443"),
                loadSslContext(myURI, true), "");

        HttpJsonMessageWithFaultingPayload msg = new HttpJsonMessageWithFaultingPayload();
        msg.setMethod("GET");
        msg.setUri("/_cat/indices");
        msg.setProtocol("https");
        var headers = new ListKeyAdaptingCaseInsensitiveHeadersMap(new StrictCaseInsensitiveHttpHeadersMap());
        //headers.put("Host", HOSTNAME_STRING);
        headers.put("authorization", "Basic YWRtaW46QWRtaW4xMjMh");
        msg.setHeaders(headers);

        var key_id = System.getenv("AWS_ACCESS_KEY_ID");
        var secret_key = System.getenv("AWS_SECRET_ACCESS_KEY");
        var token = System.getenv("AWS_SESSION_TOKEN");
        System.setProperty("aws.accessKeyId", key_id);
        System.setProperty("aws.secretAccessKey", secret_key);
        System.setProperty("aws.sessionToken", token);
        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        var resolvedValue = credentialsProvider.resolveCredentials();

        signer = new SigV4Signer(msg, credentialsProvider);
        Map<String, List<String>> signedHeaders = signer.getSignatureheaders(service, region);

        var headersAsAString =  msg.headers().entrySet().stream()
                .map(kvp->String.format("%1$s: %2$s", kvp.getKey(), ((List<String>)kvp.getValue()).get(0)))
                .collect(Collectors.joining("\n"));

        var signedHeadersString = signedHeaders.entrySet().stream()
                .map(kvp -> String.format("%s: %s", kvp.getKey(), kvp.getValue().get(0)))
                .collect(Collectors.joining("\n"));

        String mergedHeadersString = headersAsAString + "\n" + signedHeadersString;

        byte[] arr = new byte[9999]; // YOUR REQUEST AS A BYTE ARRAY
        var requestString = String.format("%1$s %2$s %3$s" +
                        "%4$s\r",
                msg.method(), msg.uri(),// HttpVersion.HTTP_1_1,
                mergedHeadersString);
        arr = requestString.getBytes();

        nettyHandler.consumeBytes(arr);
        AggregatedRawResponse response = nettyHandler.finalizeRequest().get();
        var uglyAnswer = response.responsePackets.stream()
                .map(kvp -> kvp.getValue())
                .map(s -> new String(s, StandardCharsets.UTF_8))
                .collect(Collectors.joining("\n"));
        System.out.println(uglyAnswer);
        assertEquals(true,true);
*/
        client = new SimpleHttpClientForTesting(false);
        HttpJsonMessageWithFaultingPayload msg = new HttpJsonMessageWithFaultingPayload();
        msg.setMethod("PUT");
        msg.setUri("/ok92worked");
        msg.setProtocol("https");
        var key_id = System.getenv("AWS_ACCESS_KEY_ID");
        var secret_key = System.getenv("AWS_SECRET_ACCESS_KEY");
        var token = System.getenv("AWS_SESSION_TOKEN");
        System.setProperty("aws.accessKeyId", key_id);
        System.setProperty("aws.secretAccessKey", secret_key);
        System.setProperty("aws.sessionToken", token);

        signer = new SigV4Signer(msg, DefaultCredentialsProvider.create());

        URI endpoint = new URI("https://6k6etwtuh12fwuey0h8h.us-east-1.aoss.amazonaws.com/ok92worked");

        // Get the signed headers
        Map<String, List<String>> signedHeaders = signer.getSignatureheaders("aoss", "us-east-1");

        // Convert to List<Map.Entry<String, String>>
        List<Map.Entry<String, String>> headerEntries = signedHeaders.entrySet().stream()
                .map(kvp -> Map.entry(kvp.getKey(), kvp.getValue().get(0)))
                .collect(Collectors.toList());

        // Print request before
        headerEntries.forEach(header -> System.out.println(header.getKey() + ": " + header.getValue()));

        // Convert list back to stream
        Stream<Map.Entry<String, String>> requestHeaders = headerEntries.stream();

        // Making the request
        SimpleHttpResponse response = client.makePutRequest(endpoint, requestHeaders, null);

        // Print request after
        headerEntries.forEach(header -> System.out.println(header.getKey() + ": " + header.getValue()));

        assertEquals(200, 200);


    }

    @SneakyThrows
    @Test
    void testEmptyBodySignedRequestTest() {
        client = new SimpleHttpClientForTesting(false);
        HttpJsonMessageWithFaultingPayload msg = new HttpJsonMessageWithFaultingPayload();
        msg.setMethod("GET");
        msg.setUri("/_cat/indices");
        msg.setProtocol("https");
        var key_id = System.getenv("AWS_ACCESS_KEY_ID");
        var secret_key = System.getenv("AWS_SECRET_ACCESS_KEY");
        var token = System.getenv("AWS_SESSION_TOKEN");
        System.setProperty("aws.accessKeyId", key_id);
        System.setProperty("aws.secretAccessKey", secret_key);
        System.setProperty("aws.sessionToken", token);

        signer = new SigV4Signer(msg, DefaultCredentialsProvider.create());

        URI endpoint = new URI("https://6k6etwtuh12fwuey0h8h.us-east-1.aoss.amazonaws.com/_cat/indices");

        Map<String, List<String>> signedHeaders = signer.getSignatureheaders("aoss", "us-east-1");

        // Convert to List<Map.Entry<String, String>>
        List<Map.Entry<String, String>> headerEntries = signedHeaders.entrySet().stream()
                .map(kvp -> Map.entry(kvp.getKey(), kvp.getValue().get(0)))
                .collect(Collectors.toList());

        // Print headers before
        headerEntries.forEach(header -> System.out.println(header.getKey() + ": " + header.getValue()));

        // Convert list back to stream
        Stream<Map.Entry<String, String>> requestHeaders = headerEntries.stream();

        // Making the request
        SimpleHttpResponse response = client.makeGetRequest(endpoint, requestHeaders);

        // Print headers after
        headerEntries.forEach(header -> System.out.println(header.getKey() + ": " + header.getValue()));

        assertEquals(200, 200);

    }

    private String getBasicAuthHeaderValue(String username, String password) {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encodedAuth);
    }



}

