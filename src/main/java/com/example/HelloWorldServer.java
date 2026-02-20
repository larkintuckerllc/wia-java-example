package com.example;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

public class HelloWorldServer {

    private static final int PORT = 50051;
    private static final String CREDENTIALS_DIR = "production".equals(System.getenv("APP_ENV"))
            ? "/var/run/secrets/workload-spiffe-credentials"
            : "var/run/secrets/workload-spiffe-credentials";

    private Server server;

    public void start() throws IOException {
        var sslContextBuilder = GrpcSslContexts
                .forServer(
                        new File(CREDENTIALS_DIR + "/certificates.pem"),
                        new File(CREDENTIALS_DIR + "/private_key.pem"))
                .trustManager(spiffeTrustManager(new File(CREDENTIALS_DIR + "/ca_certificates.pem")))
                .clientAuth(ClientAuth.REQUIRE);

        server = NettyServerBuilder.forPort(PORT)
                .addService(new GreeterImpl())
                .sslContext(sslContextBuilder.build())
                .build()
                .start();
        System.out.println("Server started, listening on port " + PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down gRPC server...");
            try {
                stop();
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
        }));
    }

    // Validates the client certificate chain against the CA and enforces that the
    // client presents the expected SPIFFE URI in its certificate's URI SAN.
    private static X509ExtendedTrustManager spiffeTrustManager(File caCertFile) throws IOException {
        try {
            var cf = CertificateFactory.getInstance("X.509");
            var caCert = (X509Certificate) cf.generateCertificate(new FileInputStream(caCertFile));
            var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("ca", caCert);
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            var delegate = (X509TrustManager) tmf.getTrustManagers()[0];

            return new X509ExtendedTrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) throws CertificateException {
                    delegate.checkClientTrusted(c, a);
                    SpiffeValidator.verifySpiffeUri(c[0]);
                }
                public void checkServerTrusted(X509Certificate[] c, String a) throws CertificateException { delegate.checkServerTrusted(c, a); }
                public X509Certificate[] getAcceptedIssuers() { return delegate.getAcceptedIssuers(); }
                public void checkClientTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { checkClientTrusted(c, a); }
                public void checkServerTrusted(X509Certificate[] c, String a, Socket s) throws CertificateException { delegate.checkServerTrusted(c, a); }
                public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { checkClientTrusted(c, a); }
                public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) throws CertificateException { delegate.checkServerTrusted(c, a); }
            };
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to load SPIFFE trust manager", e);
        }
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        HelloWorldServer server = new HelloWorldServer();
        server.start();
        server.blockUntilShutdown();
    }
}
