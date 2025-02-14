# Cassandra Module

## Usage example

This example connects to the Cassandra cluster:

1. Define a container:
    <!--codeinclude-->
    [Container definition](../../../modules/cassandra/src/test/java/org/testcontainers/cassandra/CassandraContainerTest.java) inside_block:container-definition
    <!--/codeinclude-->

2. Build a `CqlSession`:
    <!--codeinclude-->
    [Building CqlSession](../../../modules/cassandra/src/test/java/org/testcontainers/cassandra/CassandraContainerTest.java) inside_block:cql-session
    <!--/codeinclude-->

3. Define a container with custom `cassandra.yaml` located in a directory `cassandra-auth-required-configuration`:
    
    <!--codeinclude-->
    [Running init script with required authentication](../../../modules/cassandra/src/test/java/org/testcontainers/cassandra/CassandraContainerTest.java) inside_block:init-with-auth
    <!--/codeinclude-->

## Using secure connection (TLS)

If you override the default `cassandra.yaml` with a version setting the property `client_encryption_options.optional` 
to `false`, you have to provide a valid client certificate and key (PEM format) when you initialize your container:

<!--codeinclude-->
[SSL setup](../../../modules/cassandra/src/test/java/org/testcontainers/cassandra/CassandraContainerTest.java) inside_block:with-ssl-config
<!--/codeinclude-->

!!! hint
    To generate the client certificate and key, please refer to
    [this documentation](https://docs.datastax.com/en/cassandra-oss/3.x/cassandra/configuration/secureSSLCertificates.html). 

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:cassandra:{{latest_version}}"
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>cassandra</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```
