# Mockserver Module

Mock Server can be used to mock HTTP services by matching requests against user-defined expectations.

## Usage example

The following example shows how to start Mockserver.

<!--codeinclude-->
[Creating a MockServer container](../../modules/mockserver/src/test/java/org/testcontainers/containers/MockServerContainerRuleTest.java) inside_block:creatingProxy
<!--/codeinclude-->

And how to set a simple expectation using the Java MockServerClient.

<!--codeinclude-->
[Setting a simple expectation](../../modules/mockserver/src/test/java/org/testcontainers/containers/MockServerContainerRuleTest.java) inside_block:testSimpleExpectation
<!--/codeinclude-->

See [SimpleHttpClient.java](../../modules/mockserver/src/test/java/org/testcontainers/containers/SimpleHttpClient.java) for implentation details of the `responseFromMockserver` method used above.

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:mockserver:{{latest_version}}"
    ```
=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mockserver</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```

Additionally, don't forget to add a [client dependency `org.mock-server:mockserver-client-java`](https://search.maven.org/search?q=mockserver-client-java) 
to be able to set expectations, it's not provided by the testcontainers module. Client version should match to the version in a container tag.
