package com.example;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public class HelloWorldClient {

    private final GreeterGrpc.GreeterBlockingStub blockingStub;

    public HelloWorldClient(Channel channel) {
        blockingStub = GreeterGrpc.newBlockingStub(channel);
    }

    public void greet(String name) {
        HelloRequest request = HelloRequest.newBuilder().setName(name).build();
        HelloReply response;
        try {
            response = blockingStub.sayHello(request);
        } catch (StatusRuntimeException e) {
            System.err.println("RPC failed: " + e.getStatus());
            return;
        }
        System.out.println("Greeting: " + response.getMessage());
    }

    // Validates the server certificate chain against the CA, skips hostname
    // verification (SPIFFE certs use URI SANs, not DNS SANs), and enforces that
    // the server presents the expected SPIFFE URI in its certificate's URI SAN.
    private static X509ExtendedTrustManager spiffeTrustManager(File caCertFile) throws Exception {
        var cf = CertificateFactory.getInstance("X.509");
        var caCert = (X509Certificate) cf.generateCertificate(new FileInputStream(caCertFile));
        var ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("ca", caCert);
        var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        var delegate = (X509TrustManager) tmf.getTrustManagers()[0];

        return new X509ExtendedTrustManager() {
            public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException { delegate.checkClientTrusted(c, a); }
            public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException {
                delegate.checkServerTrusted(c, a);
                SpiffeValidator.verifySpiffeUri(c[0]);
            }
            public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
            // 3-arg overloads are where hostname verification is injected by the SSL engine.
            // checkServerTrusted delegates to our 2-arg version to skip hostname checking
            // while still enforcing CA chain validation and the SPIFFE URI check.
            public void checkClientTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { delegate.checkClientTrusted(c, a); }
            public void checkServerTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { checkServerTrusted(c, a); }
            public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { delegate.checkClientTrusted(c, a); }
            public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { checkServerTrusted(c, a); }
        };
    }

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "World";
        String credDir = "production".equals(System.getenv("APP_ENV"))
                ? "/var/run/secrets/workload-spiffe-credentials"
                : "var/run/secrets/workload-spiffe-credentials";
        var sslContextBuilder = GrpcSslContexts.configure(SslContextBuilder.forClient())
                .trustManager(spiffeTrustManager(new File(credDir + "/ca_certificates.pem")))
                .keyManager(
                        new File(credDir + "/certificates.pem"),
                        new File(credDir + "/private_key.pem"));
        String host = System.getenv().getOrDefault("GRPC_SERVER_HOST", "localhost");
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, 50051)
                .sslContext(sslContextBuilder.build())
                .build();
        try {
            HelloWorldClient client = new HelloWorldClient(channel);
            client.greet(name);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
