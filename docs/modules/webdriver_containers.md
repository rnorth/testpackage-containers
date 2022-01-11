# Webdriver Containers

Testcontainers can be used to automatically instantiate and manage containers that include web browsers, such as those
from SeleniumHQ's [docker-selenium](https://github.com/SeleniumHQ/docker-selenium) project.

## Benefits

* Fully compatible with Selenium 3 & 4 tests, by providing a `RemoteWebDriver` instance
* No need to have specific web browsers, or even a desktop environment, installed on test servers. The only dependency
  is a working Docker installation and your Java JUnit test suite.
* Browsers are always launched from a fixed, clean image. This means no configuration drift from user changes or
  automatic browser upgrades.
* Additionally, the use of a clean browser prevents leakage of cookies, cached data or other state between tests.
* **VNC screen recording**: Testcontainers can automatically record video of test runs (optionally capturing just
  failing tests)

Creation of browser containers is fast, so it's actually quite feasible to have a totally fresh browser instance for
every test.

## Example

The following field in your JUnit UI test class will prepare a container running Chrome:
<!--codeinclude-->
[Chrome](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeWebDriverContainerTest.java) inside_block:junitRule
<!--/codeinclude-->

        
Now, instead of instantiating an instance of WebDriver directly, use the following to obtain an instance inside your
test methods:
<!--codeinclude-->
[RemoteWebDriver](../../modules/selenium/src/test/java/org/testcontainers/junit/LocalServerWebDriverContainerTest.java) inside_block:getWebDriver
<!--/codeinclude-->

You can then use this driver instance like a regular WebDriver.

Note that, if you want to test a **web application running on the host machine** (the machine the JUnit tests are
running on - which is quite likely), you'll need to replace any references to `localhost` with an IP address that the
Docker container can reach. Use the `getTestHostIpAddress()` method, e.g.:
<!--codeinclude-->
[Open Web Page](../../modules/selenium/src/test/java/org/testcontainers/junit/LocalServerWebDriverContainerTest.java) inside_block:getPage
<!--/codeinclude-->


## Options

### Other browsers

At the moment, Chrome and Firefox are supported. To switch, simply change the first parameter to the rule constructor:
<!--codeinclude-->
[Chrome](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeWebDriverContainerTest.java) inside_block:junitRule
[Firefox](../../modules/selenium/src/test/java/org/testcontainers/junit/FirefoxWebDriverContainerTest.java) inside_block:junitRule
<!--/codeinclude-->

### Recording videos

By default, no videos will be recorded. However, you can instruct Testcontainers to capture videos for all tests or
just for failing tests.

<!--codeinclude-->
[Record all Tests](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeRecordingWebDriverContainerTest.java) inside_block:recordAll
[Record failing Tests](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeRecordingWebDriverContainerTest.java) inside_block:recordFailing
<!--/codeinclude-->

Note that the second parameter of `withRecordingMode` should be a directory where recordings can be saved.

By default, the video will be recorded in [FLV](https://en.wikipedia.org/wiki/Flash_Video) format, but you can specify it explicitly or change it to [MP4](https://en.wikipedia.org/wiki/MPEG-4_Part_14) using `withRecordingMode` method with `VncRecordingFormat` option:

<!--codeinclude-->
[Video Format in MP4](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeRecordingWebDriverContainerTest.java) inside_block:recordMp4
[Video Format in FLV](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeRecordingWebDriverContainerTest.java) inside_block:recordFlv
<!--/codeinclude-->

If you would like to customise the file name of the recording, or provide a different directory at runtime based on the description of the test and/or its success or failure, you may provide a custom recording file factory as follows:
<!--codeinclude-->
[CustomRecordingFileFactory](../../modules/selenium/src/test/java/org/testcontainers/junit/ChromeRecordingWebDriverContainerTest.java) inside_block:withRecordingFileFactory
<!--/codeinclude-->


Note the factory must implement `org.testcontainers.containers.RecordingFileFactory`.

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

```groovy tab='Gradle'
testImplementation "org.testcontainers:selenium:{{latest_version}}"
```

```xml tab='Maven'
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>selenium</artifactId>
    <version>{{latest_version}}</version>
    <scope>test</scope>
</dependency>
```

!!! hint
    Adding this Testcontainers library JAR will not automatically add a Selenium Webdriver JAR to your project. 
    You should ensure that your project also has suitable Selenium dependencies in place, for example:

    ```groovy tab='Gradle'
    implementation 'org.seleniumhq.selenium:selenium-java:4.1.1'
    ```
    
    ```xml tab='Maven'
    <dependency>
        <groupId>org.seleniumhq.selenium</groupId>
        <artifactId>selenium-java</artifactId>
        <version>4.1.1</version>
    </dependency>
    ```
