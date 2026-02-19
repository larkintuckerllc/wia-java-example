# wia-java-example

A simple Hello World Java application built with Maven.

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

Compile the project:

```bash
mvn compile
```

Run the application:

```bash
mvn exec:java -Dexec.mainClass=com.example.App
```
