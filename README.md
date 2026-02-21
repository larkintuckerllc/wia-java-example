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

For local use only (current machine architecture):

```bash
docker build -f Dockerfile.server -t grpc-server .
docker build -f Dockerfile.client -t grpc-client .
```

### Building and pushing multi-platform images

To build images that run on both `amd64` and `arm64` (e.g. pushing to a registry
for deployment on GKE), use `docker buildx`. Multi-platform images must be pushed
directly to a registry and cannot be loaded into the local Docker daemon.

Create a buildx builder the first time:

```bash
docker buildx create --name multiarch --use
```

Build and push:

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f Dockerfile.server \
  -t gcr.io/jtucker-wia-d/grpc-server:0.1.0 \
  --push \
  .

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f Dockerfile.client \
  -t gcr.io/jtucker-wia-d/grpc-client:0.1.0 \
  --push \
  .
```

This pushes a manifest list to the registry containing a separate image variant
for each architecture. Nodes automatically pull the correct variant for their
architecture.

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

## Kubernetes

The server can be deployed as a Pod on GKE using the GKE Certificate Controller
CSI driver to mount SPIFFE credentials automatically. The pod runs in the `debug`
namespace under the `default` service account, matching the SPIFFE URI
`spiffe://jtucker-wia-d.svc.id.goog/ns/debug/sa/default` embedded in the workload
certificate. The `trustDomain` in the volume attribute must match the fleet trust
domain configured in the cluster.

```yaml
apiVersion: v1
kind: Pod
metadata:
  namespace: debug
  name: grpc-server
  labels:
    app: grpc-server
spec:
  serviceAccountName: default
  containers:
  - name: grpc-server
    image: gcr.io/jtucker-wia-d/grpc-server:0.1.0
    ports:
    - containerPort: 50051
    volumeMounts:
    - name: fleet-spiffe-credentials
      mountPath: /var/run/secrets/workload-spiffe-credentials
      readOnly: true
  volumes:
  - name: fleet-spiffe-credentials
    csi:
      driver: podcertificate.gke.io
      volumeAttributes:
        signerName: spiffe.gke.io/fleet-svid
        trustDomain: fleet-project/svc.id.goog
```

### Service

Create a Service to expose the server pod within the cluster. The selector
matches the `app: grpc-server` label on the pod.

```yaml
apiVersion: v1
kind: Service
metadata:
  namespace: debug
  name: grpc-server
spec:
  selector:
    app: grpc-server
  ports:
  - port: 50051
    targetPort: 50051
```

### Running the client as a Job

The client can be run as a Kubernetes Job, which connects to the server pod and
exits on completion.

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  namespace: debug
  name: grpc-client
spec:
  template:
    spec:
      serviceAccountName: default
      restartPolicy: Never
      containers:
      - name: grpc-client
        image: gcr.io/jtucker-wia-d/grpc-client:0.1.0
        args: ["Alice"]
        env:
        - name: GRPC_SERVER_HOST
          value: grpc-server
        volumeMounts:
        - name: fleet-spiffe-credentials
          mountPath: /var/run/secrets/workload-spiffe-credentials
          readOnly: true
      volumes:
      - name: fleet-spiffe-credentials
        csi:
          driver: podcertificate.gke.io
          volumeAttributes:
            signerName: spiffe.gke.io/fleet-svid
            trustDomain: fleet-project/svc.id.goog
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
