# Typesense

Testcontainers module for [Typesense](https://hub.docker.com/r/typesense/typesense).

## TypesenseContainer's usage examples

You can start a Typesense container instance from any Java application by using:

<!--codeinclude-->
[Typesense container](../../modules/typesense/src/test/java/org/testcontainers/typesense/TypesenseContainerTest.java) inside_block:container
<!--/codeinclude-->

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:typesense:{{latest_version}}"
    ```

=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>typesense</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```
