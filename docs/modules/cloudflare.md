# Cloudflare Module

Testcontainers module for Cloudflare Quick Tunnels(https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/) for exposing your app to the internet. 

This module is intended to be used for testing components that need to be exposed to public internet - for example to receive hooks from public cloud.
Or to show your local state of the application to friends. 

## Usage example

Start a Cloudflared container as follows:

<!--codeinclude-->
[Starting a Cloudflared Container](../../modules/cloudflare/src/test/java/org/testcontainers/cloudflare/CloudflaredContainerTest.java) inside_block:starting
<!--/codeinclude-->

### Getting the public Url

`Cloudflared` contaienr exposes a port on your host, via a [Quick tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/).
To get the public url on which this port is available to the internet, call the `getPublicUrl` method. 

<!--codeinclude-->
[Get the public Url](../../modules/cloudflare/src/test/java/org/testcontainers/cloudflare/CloudflaredContainerTest.java) inside_block:get_public_url
<!--/codeinclude-->

## Known limitations

!!! warning
    * From the Cloudflare docs: "Quick Tunnels are subject to a hard limit on the number of concurrent requests that can be proxied at any point in time. Currently, this limit is 200 in-flight requests. If a Quick Tunnel hits this limit, the HTTP response will return a 429 status code."

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:cloudflare:{{latest_version}}"
    ```
=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>cloudflare</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```
