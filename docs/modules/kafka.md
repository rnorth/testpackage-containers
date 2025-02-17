# Kafka Module

Testcontainers can be used to automatically instantiate and manage [Apache Kafka](https://kafka.apache.org) containers.

Currently, two different Kafka images are supported:

* `org.testcontainers.kafka.ConfluentKafkaContainer` supports 
[confluentinc/cp-kafka](https://hub.docker.com/r/confluentinc/cp-kafka/)
* `org.testcontainers.kafka.KafkaContainer` supports [apache/kafka](https://hub.docker.com/r/apache/kafka/) and [apache/kafka-native](https://hub.docker.com/r/apache/kafka-native/)

!!! note
    `org.testcontainers.containers.KafkaContainer` is deprecated.
    Please use `org.testcontainers.kafka.ConfluentKafkaContainer` or `org.testcontainers.kafka.KafkaContainer` instead, depending on the used image.

## Benefits

* Running a single node Kafka installation with just one line of code
* No need to manage external Zookeeper installation, required by Kafka. But see [below](#zookeeper)

## Example

### Using org.testcontainers.containers.KafkaContainer

Create a `KafkaContainer` to use it in your tests:

<!--codeinclude-->
[Creating a KafkaContainer](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:constructorWithVersion
<!--/codeinclude-->

The correspondence between Confluent Platform versions and Kafka versions can be seen [in Confluent documentation](https://docs.confluent.io/current/installation/versions-interoperability.html#cp-and-apache-kafka-compatibility)

Now your tests or any other process running on your machine can get access to running Kafka broker by using the following bootstrap server location:

<!--codeinclude-->
[Bootstrap Servers](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:getBootstrapServers
<!--/codeinclude-->

### Using org.testcontainers.kafka.ConfluentKafkaContainer

!!! note
    Compatible with `confluentinc/cp-kafka` images version `7.4.0` and later.

Create a `ConfluentKafkaContainer` to use it in your tests:

<!--codeinclude-->
[Creating a ConfluentKafkaContainer](../../modules/kafka/src/test/java/org/testcontainers/kafka/ConfluentKafkaContainerTest.java) inside_block:constructorWithVersion
<!--/codeinclude-->

### Using org.testcontainers.kafka.KafkaContainer

Create a `KafkaContainer` to use it in your tests:

<!--codeinclude-->
[Creating a KafkaContainer](../../modules/kafka/src/test/java/org/testcontainers/kafka/KafkaContainerTest.java) inside_block:constructorWithVersion
<!--/codeinclude-->

## Options
        
### <a name="zookeeper"></a> Using external Zookeeper

!!! note
    Only available for `org.testcontainers.containers.KafkaContainer`

If for some reason you want to use an externally running Zookeeper, then just pass its location during construction:
<!--codeinclude-->
[External Zookeeper](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:withExternalZookeeper
<!--/codeinclude-->

### Using Kraft mode

!!! note
    Only available for `org.testcontainers.containers.KafkaContainer`

KRaft mode was declared production ready in 3.3.1 (confluentinc/cp-kafka:7.3.x) 

<!--codeinclude-->
[Kraft mode](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:withKraftMode
<!--/codeinclude-->

See the [versions interoperability matrix](https://docs.confluent.io/platform/current/installation/versions-interoperability.html) for more details.

### Register listeners

There are scenarios where additional listeners are needed because the consumer/producer can be in another
container in the same network or a different process where the port to connect differs from the default exposed port. E.g [Toxiproxy](../../modules/toxiproxy/).

<!--codeinclude-->
[Register additional listener](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:registerListener
<!--/codeinclude-->

Container defined in the same network:

<!--codeinclude-->
[Create kcat container](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:createKCatContainer
<!--/codeinclude-->

Client using the new registered listener:

<!--codeinclude-->
[Produce/Consume via new listener](../../modules/kafka/src/test/java/org/testcontainers/containers/KafkaContainerTest.java) inside_block:produceConsumeMessage
<!--/codeinclude-->

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:kafka:{{latest_version}}"
    ```
=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```
