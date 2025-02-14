# Custom configuration

You can override some default properties if your environment requires that.

## Configuration locations
The configuration will be loaded from multiple locations. Properties are considered in the following order:

1. Environment variables
2. `.testcontainers.properties` in user's home folder. Example locations:  
**Linux:** `/home/myuser/.testcontainers.properties`  
**Windows:** `C:/Users/myuser/.testcontainers.properties`  
**macOS:** `/Users/myuser/.testcontainers.properties`
3. `testcontainers.properties` on the classpath.

Note that when using environment variables, configuration property names should be set in upper 
case with underscore separators, preceded by `TESTCONTAINERS_` - e.g. `checks.disable` becomes 
`TESTCONTAINERS_CHECKS_DISABLE`.

The classpath `testcontainers.properties` file may exist within the local codebase (e.g. within the `src/test/resources` directory) or within library dependencies that you may have. 
Any such configuration files will have their contents merged.
If any keys conflict, the value will be taken on the basis of the first value found in:

* 'local' classpath (i.e. where the URL of the file on the classpath begins with `file:`), then
* other classpath locations (i.e. JAR files) - considered in _alphabetical order of path_  to provide deterministic ordering.

## Disabling the startup checks
> **checks.disable = [true|false]**

Before running any containers Testcontainers will perform a set of startup checks to ensure that your environment is configured correctly. Usually they look like this:
```
        ℹ︎ Checking the system...
        ✔ Docker version should be at least 1.6.0
        ✔ File should be mountable
        ✔ A port exposed by a docker container should be accessible
```
It takes a couple of seconds, but if you want to speed up your tests, you can disable the checks once you have everything configured. Add `checks.disable=true` to your `$HOME/.testcontainers.properties` to completely disable them.

## Customizing images

!!! note
    This approach is discouraged and deprecated, but is documented for completeness.
    Overriding individual image names via configuration may be removed in 2021.
    See [Image Name Substitution](./image_name_substitution.md) for other strategies for substituting image names to pull from other registries.


Testcontainers uses public Docker images to perform different actions like startup checks, VNC recording and others. 
Some companies disallow the usage of Docker Hub, but you can override `*.image` properties with your own images from your private registry to workaround that.

> **ryuk.container.image = testcontainers/ryuk:0.3.3**
> Performs fail-safe cleanup of containers, and always required (unless [Ryuk is disabled](#disabling-ryuk))

> **tinyimage.container.image = alpine:3.17**  
> Used to check whether images can be pulled at startup, and always required (unless [startup checks are disabled](#disabling-the-startup-checks))

> **sshd.container.image = testcontainers/sshd:1.1.0**  
> Required if [exposing host ports to containers](./networking.md#exposing-host-ports-to-the-container)

> **vncrecorder.container.image = testcontainers/vnc-recorder:1.3.0**
> Used by VNC recorder in Testcontainers' Selenium integration

> **socat.container.image = alpine/socat**  
> **compose.container.image = docker/compose:1.8.0**  
> Required if using [Docker Compose](../modules/docker_compose.md)

> **kafka.container.image = confluentinc/cp-kafka**  
> Used by KafkaContainer 

> **localstack.container.image = localstack/localstack**  
> Used by LocalStack

> **pulsar.container.image = apachepulsar/pulsar:2.2.0**  
> Used by Apache Pulsar

## Customizing Ryuk resource reaper

> **ryuk.container.image = testcontainers/ryuk:0.3.3**
> The resource reaper is responsible for container removal and automatic cleanup of dead containers at JVM shutdown

> **ryuk.container.privileged = true**
> In some environments ryuk must be started in privileged mode to work properly (--privileged flag)

### Disabling Ryuk
Ryuk must be started as a privileged container.  
If your environment already implements automatic cleanup of containers after the execution,
but does not allow starting privileged containers, you can turn off the Ryuk container by setting
`TESTCONTAINERS_RYUK_DISABLED` **environment variable** to `true`.

!!!tip
    Note that Testcontainers will continue doing the cleanup at JVM's shutdown, unless you `kill -9` your JVM process.

## Customizing image pull behaviour

> **pull.timeout = 120**
> By default Testcontainers will timeout if pull takes more than this duration (in seconds)

> **pull.pause.timeout = 30**
> By default Testcontainers will abort the pull of an image if the pull appears stalled (no data transferred) for longer than this duration (in seconds).

## Customizing client ping behaviour

> **client.ping.timeout = 10**
> Specifies for how long Testcontainers will try to connect to the Docker client to obtain valid info about the client before giving up and trying next strategy, if applicable (in seconds).

## Customizing Docker host detection

Testcontainers will attempt to detect the Docker environment and configure everything to work automatically.

However, sometimes customization is required. Testcontainers will respect the following **environment variables**:

> **DOCKER_HOST** = unix:///var/run/docker.sock  
> See [Docker environment variables](https://docs.docker.com/engine/reference/commandline/cli/#environment-variables)
>
> **TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE**  
> Path to Docker's socket. Used by Ryuk, Docker Compose, and a few other containers that need to perform Docker actions.  
> Example: `/var/run/docker-alt.sock`
> 
> **TESTCONTAINERS_HOST_OVERRIDE**  
> Docker's host on which ports are exposed.  
> Example: `docker.svc.local`

For advanced users, the Docker host connection can be configured **via configuration** in `~/.testcontainers.properties`.
Note that these settings require use of the `EnvironmentAndSystemPropertyClientProviderStrategy`. The example below 
illustrates usage:

```properties
docker.client.strategy=org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy
docker.host=tcp\://my.docker.host\:1234         # Equivalent to the DOCKER_HOST environment variable. Colons should be escaped.
docker.socket.override=/var/run/docker-alt.sock # Equivalent to the TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE environment variable.
docker.tls.verify=1                             # Equivalent to the DOCKER_TLS_VERIFY environment variable
docker.cert.path=/some/path                     # Equivalent to the DOCKER_CERT_PATH environment variable
```
In addition, you can deactivate this behaviour by specifying:
```properties
dockerconfig.source=autoIgnoringUserProperties # 'auto' by default
```
