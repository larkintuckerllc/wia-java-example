package com.example;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

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
                .trustManager(new File(CREDENTIALS_DIR + "/ca_certificates.pem"))
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
