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

## Project Structure

```
src/
├── main/
│   ├── java/com/example/
│   │   ├── GreeterImpl.java       # gRPC service implementation
│   │   ├── HelloWorldServer.java  # Server entry point
│   │   └── HelloWorldClient.java  # Client entry point
│   └── proto/
│       └── helloworld.proto       # Service definition
```

Java classes for the proto messages and service stub are generated into
`target/generated-sources/` during `mvn compile` and are not committed to source control.
