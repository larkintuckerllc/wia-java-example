# wia-java-example

A Hello World gRPC service implemented with gRPC-Java and Maven.

## Prerequisites

Install [SDKMAN!](https://sdkman.io) if you don't already have it:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
```

## Setting Up the Development Environment

Install Java (Amazon Corretto 25):

```bash
sdk install java 25.0.1-amzn
sdk use java 25.0.1-amzn
```

Install Maven 3.9.12:

```bash
sdk install maven 3.9.12
sdk use maven 3.9.12
```

Verify the installations:

```bash
java -version
mvn -version
```

## Building and Running

The `exec:java` goal does not automatically trigger compilation, so always chain
`compile` before it. This also ensures the Java classes are generated from the
proto file before they are needed.

### Running the server

```bash
mvn compile exec:java -Dexec.mainClass=com.example.HelloWorldServer
```

The server listens on port `50051`.

### Running the client

In a separate terminal, with the server already running:

```bash
mvn compile exec:java -Dexec.mainClass=com.example.HelloWorldClient
```

You can optionally pass a name as an argument:

```bash
mvn compile exec:java -Dexec.mainClass=com.example.HelloWorldClient -Dexec.args="Alice"
```

## Docker

### Building the images

```bash
docker build -f Dockerfile.server -t grpc-server .
docker build -f Dockerfile.client -t grpc-client .
```

### Running the containers

The containers expect the SPIFFE credentials to be mounted at
`/var/run/secrets/workload-spiffe-credentials`. The directory must contain:

| File | Description |
|---|---|
| `ca_certificates.pem` | CA certificate used to verify peers |
| `certificates.pem` | This workload's certificate |
| `private_key.pem` | This workload's private key |

Start the server, replacing `/path/to/creds` with the local directory containing
the credential files:

```bash
docker run --rm \
  -p 50051:50051 \
  -v /path/to/creds:/var/run/secrets/workload-spiffe-credentials:ro \
  grpc-server
```

In a separate terminal, run the client against the server container. The optional
argument is the name to greet (defaults to `World`):

```bash
docker run --rm \
  -v /path/to/creds:/var/run/secrets/workload-spiffe-credentials:ro \
  grpc-client Alice
```

If the client and server are running in separate containers on the same host
without a shared network, pass `--network host` so the client can reach the
server on `localhost:50051`:

```bash
docker run --rm \
  --network host \
  -v /path/to/creds:/var/run/secrets/workload-spiffe-credentials:ro \
  grpc-client Alice
```

## Project Structure

```
├── Dockerfile.client              # Client container image
├── Dockerfile.server              # Server container image
└── src/
    └── main/
        ├── java/com/example/
        │   ├── GreeterImpl.java       # gRPC service implementation
        │   ├── HelloWorldServer.java  # Server entry point
        │   ├── HelloWorldClient.java  # Client entry point
        │   └── SpiffeValidator.java   # SPIFFE URI verification
        └── proto/
            └── helloworld.proto       # Service definition
```

Java classes for the proto messages and service stub are generated into
`target/generated-sources/` during `mvn compile` and are not committed to source control.
