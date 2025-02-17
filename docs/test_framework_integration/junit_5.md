# Jupiter / JUnit 5

While Testcontainers is tightly coupled with the JUnit 4.x rule API, this module provides
an API that is based on the [JUnit Jupiter](https://junit.org/junit5/) extension model.

The extension supports two modes:

- containers that are restarted for every test method
- containers that are shared between all methods of a test class

Note that Jupiter/JUnit 5 integration is packaged as a separate library JAR; see [below](#adding-testcontainers-junit-5-support-to-your-project-dependencies) for details.

## Extension

Jupiter integration is provided by means of the `@Testcontainers` annotation.
  
The extension finds all fields that are annotated with `@Container` and calls their container lifecycle 
methods (methods on the `Startable` interface). Containers declared as static fields will be shared between test 
methods. They will be started only once before any test method is executed and stopped after the last test method has 
executed. Containers declared as instance fields will be started and stopped for every test method.
  
**Note:** This extension has only been tested with sequential test execution. Using it with parallel test execution is 
unsupported and may have unintended side effects.
  
*Example:*
<!--codeinclude-->
[Mixed Lifecycle](../../modules/junit-jupiter/src/test/java/org/testcontainers/junit/jupiter/MixedLifecycleTests.java) inside_block:testClass
<!--/codeinclude-->

## Examples

To use the Testcontainers extension annotate your test class with `@Testcontainers`.

### Restarted containers

To define a restarted container, define an instance field inside your test class and annotate it with
the `@Container` annotation.

<!--codeinclude-->
[Restarted Containers](../../modules/junit-jupiter/src/test/java/org/testcontainers/junit/jupiter/TestcontainersNestedRestartedContainerTests.java) inside_block:testClass
<!--/codeinclude-->


### Shared containers

Shared containers are defined as static fields in a top level test class and have to be annotated with `@Container`.
Note that shared containers can't be declared inside nested test classes.
This is because nested test classes have to be defined non-static and can't therefore have static fields.

<!--codeinclude-->
[Shared Container](../../modules/junit-jupiter/src/test/java/org/testcontainers/junit/jupiter/MixedLifecycleTests.java) lines:18-23,32-33,35-36
<!--/codeinclude-->

## Singleton containers

Note that the [singleton container pattern](manual_lifecycle_control.md#singleton-containers) is also an option when
using JUnit 5.

## Limitations

Since this module has a dependency onto JUnit Jupiter and on Testcontainers core, which
has a dependency onto JUnit 4.x, projects using this module will end up with both, JUnit Jupiter
and JUnit 4.x in the test classpath.

This extension has only been tested with sequential test execution. Using it with parallel test execution is unsupported and may have unintended side effects.

## Adding Testcontainers JUnit 5 support to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:junit-jupiter:{{latest_version}}"
    ```
=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```
