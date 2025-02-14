# Manual container lifecycle control

While Testcontainers was originally built with JUnit 4 integration in mind, it is fully usable with other test
frameworks, or with no framework at all.

## Manually starting/stopping containers

Containers can be started and stopped in code using `start()` and `stop()` methods. Additionally, container classes
implement `AutoCloseable`. This enables better assurance that the container will be stopped at the appropriate time.

```java
try (GenericContainer container = new GenericContainer("imagename")) {
    container.start();
    // ... use the container
    // no need to call stop() afterwards
    }
```

## Singleton containers

Sometimes it might be useful to define a container that is only started once for several test classes.
There is no special support for this use case provided by the Testcontainers extension.
Instead this can be implemented using the following pattern:

```java
abstract class AbstractContainerBaseTest {

    static final MySQLContainer MY_SQL_CONTAINER;

    static {
        MY_SQL_CONTAINER = new MySQLContainer();
        MY_SQL_CONTAINER.start();
    }
}

class FirstTest extends AbstractContainerBaseTest {

    @Test
    void someTestMethod() {
        String url = MY_SQL_CONTAINER.getJdbcUrl();

        // create a connection and run test as normal
    }
}
```

The singleton container is started only once when the base class is loaded.
The container can then be used by all inheriting test classes.
At the end of the test suite the [Ryuk container](https://github.com/testcontainers/moby-ryuk)
that is started by Testcontainers core will take care of stopping the singleton container.

Please keep in mind that putting the container in a static block will influence the configuration of the container.
You will not be able to use methods such as `forResponsePredicate` by just providing a Lambda expression, you will have to use
anonymous classes, or alternatively provide them from non-abstract class.
This is not due to the limitation of `TestContainers`, but because of how they work under the hood, namely that lambdas get compiled
to static methods on a class level. Since your container is in a static block, the container gets created
before your parent and children classes get initialized and as such you cannot pass the reference to them.

Therefore, once again - it is advised to use anonymous classes in such case or full predicates coming in from different, non-abstract class.

Instead of:

```java
abstract class AbstractContainerBaseTest {

    static final GenericContainer GENERIC_CONTAINER;

    static {
        GENERIC_CONTAINER = new GENERIC_CONTAINER(
                new ImageFromDockerfile().waitingFor(Wait.forHttp('/path'))
                                         .forStatusCode(200)
                                         .forResponsePredicate(yourLambda -> yourLambda(here)) //This is never going to get executed due to NoClassDefFoundError
        );
        GENERIC_CONTAINER.start();
    }
}
```

You can do an anonymous class:

```java
abstract class AbstractContainerBaseTest {

    static final GenericContainer GENERIC_CONTAINER;

    static {
        GENERIC_CONTAINER = new GENERIC_CONTAINER(
                new ImageFromDockerfile().waitingFor(Wait.forHttp('/path'))
                                         .forStatusCode(200)
                                         .forResponsePredicate(new Predicate<String>() {
                                             
                                             @Override
                                             public boolean test(String s) {
                                                 return yourConditionHere;
                                             }
                                         }) 
        );
        
        GENERIC_CONTAINER.start();
    }
}
```
Or full predicate coming in from different class:

```java
abstract class AbstractContainerBaseTest {

    static final GenericContainer GENERIC_CONTAINER;

    static {
        GENERIC_CONTAINER = new GENERIC_CONTAINER(
                new ImageFromDockerfile().waitingFor(Wait.forHttp('/path'))
                                         .forStatusCode(200)
                                         .forResponsePredicate(PredicateHolder.getPredicate())
        );
        GENERIC_CONTAINER.start();
    }
}

public class PredicateHolder {
    
    public static Predicate<String> getPredicate() {
        return yourLambda -> yourLambda(here);
    } 
}
```
