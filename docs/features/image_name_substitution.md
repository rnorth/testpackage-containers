# Image name substitution

Testcontainers supports automatic substitution of Docker image names.

This allows replacement of an image name specified in test code with an alternative name - for example, to replace the 
name of a Docker Hub image dependency with an alternative hosted on a private image registry.

This is advisable to avoid [Docker Hub rate limiting](../supported_docker_environment/image_registry_rate_limiting.md), and some companies will prefer this for policy reasons.

This page describes four approaches for image name substitution:

* [Manual substitution](#manual-substitution) - not relying upon an automated approach
* Using an Image Name Substitutor:
    * [Developing a custom function for transforming image names on the fly](#developing-a-custom-function-for-transforming-image-names-on-the-fly)
    * [Overriding image names individually in configuration](#overriding-image-names-individually-in-configuration)

It is assumed that you have already set up a private registry hosting [all the Docker images your build requires](../supported_docker_environment/image_registry_rate_limiting.md#which-images-are-used-by-testcontainers).




## Manual substitution

Consider this if:

* You use only a few images and updating code is not a chore
* All developers and CI machines in your organisation have access to a common registry server
* You also use one of the automated mechanisms to substitute [the images that Testcontainers itself requires](../supported_docker_environment/image_registry_rate_limiting.md#which-images-are-used-by-testcontainers)

This approach simply entails modifying test code manually, e.g. changing:

For example, you may have a test that uses the `mysql` container image from Docker Hub:

<!--codeinclude--> 
[Direct Docker Hub image name](../examples/junit4/generic/src/test/java/generic/ImageNameSubstitutionTest.java) inside_block:directDockerHubReference
<!--/codeinclude-->

to:

<!--codeinclude--> 
[Private registry image name](../examples/junit4/generic/src/test/java/generic/ImageNameSubstitutionTest.java) inside_block:hardcodedMirror
<!--/codeinclude-->





## Automatically modifying Docker Hub image names

Testcontainers can be configured to modify Docker Hub image names on the fly to apply a prefix string.

Consider this if:

* Developers and CI machines need to use different image names. For example, developers are able to pull images from Docker Hub, but CI machines need to pull from a private registry
* Your private registry has copies of images from Docker Hub where the names are predictable, and just adding a prefix is enough. 
  For example, `registry.mycompany.com/mirror/mysql:8.0.36` can be derived from the original Docker Hub image name (`mysql:8.0.36`) with a consistent prefix string: `registry.mycompany.com/mirror/`

In this case, image name references in code are **unchanged**.
i.e. you would leave as-is:

<!--codeinclude--> 
[Unchanged direct Docker Hub image name](../examples/junit4/generic/src/test/java/generic/ImageNameSubstitutionTest.java) inside_block:directDockerHubReference
<!--/codeinclude-->

You can then configure Testcontainers to apply the prefix `registry.mycompany.com/mirror/` to every image that it tries to pull from Docker Hub.
This can be done in one of two ways:

* Setting environment variables `TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX=registry.mycompany.com/mirror/`
* Via config file, setting `hub.image.name.prefix` in either:
    * the `~/.testcontainers.properties` file in your user home directory, or
    * a file named `testcontainers.properties` on the classpath
    
Testcontainers will automatically apply the prefix to every image that it pulls from Docker Hub - please verify that all [the required images](../supported_docker_environment/image_registry_rate_limiting.md#which-images-are-used-by-testcontainers) exist in your registry.

Testcontainers will not apply the prefix to:

* non-Hub image names (e.g. where another registry is set)
* Docker Hub image names where the hub registry is explicitly part of the name (i.e. anything with a `docker.io` or `registry.hub.docker.com` host part)

If you want your registry to handle both official Docker Hub images (e.g `postgres`)
as well as images from other registries (e.g `mycompany/postgres`), you can use the
`TESTCONTAINERS_HUB_IMAGE_NAME_NORMALIZE` environment variable or the `hub.image.name.normalize`
configuration option. When set to `true`, Testcontainers will normalize the official Docker Hub
image names to start with `library/`. When this option is used, Testcontainers will additionally
disable image compatibility checks done by some containers, so it the compatibility responsibility
is on the user.


## Developing a custom function for transforming image names on the fly

Consider this if:

* You have complex rules about which private registry images should be used as substitutes, e.g.:
    * non-deterministic mapping of names meaning that a [name prefix](#automatically-modifying-docker-hub-image-names) cannot be used
    * rules depending upon developer identity or location
* or you wish to add audit logging of images used in the build
* or you wish to prevent accidental usage of images that are not on an approved list

In this case, image name references in code are **unchanged**.
i.e. you would leave as-is:

<!--codeinclude--> 
[Unchanged direct Docker Hub image name](../examples/junit4/generic/src/test/java/generic/ImageNameSubstitutionTest.java) inside_block:directDockerHubReference
<!--/codeinclude-->

You can implement a custom image name substitutor by:

* subclassing `org.testcontainers.utility.ImageNameSubstitutor`
* configuring Testcontainers to use your custom implementation

The following is an example image substitutor implementation:

<!--codeinclude--> 
[Example Image Substitutor](../examples/junit4/generic/src/test/java/generic/ExampleImageNameSubstitutor.java) block:ExampleImageNameSubstitutor
<!--/codeinclude-->

Testcontainers can be configured to find it at runtime via configuration.
To do this, create or modify a file on the classpath named `testcontainers.properties`.

For example:

=== "`src/test/resources/testcontainers.properties`"
    ```text
    image.substitutor=com.mycompany.testcontainers.ExampleImageNameSubstitutor
    ``` 

Note that it is also possible to provide this same configuration property:

* in a `testcontainers.properties` file at the root of a library JAR file (useful if you wish to distribute a drop-in image substitutor JAR within an organization) 
* in a properties file in the user's home directory (`~/.testcontainers.properties`; note the leading `.`)
* or as an environment variable (e.g. `TESTCONTAINERS_IMAGE_SUBSTITUTOR=com.mycompany.testcontainers.ExampleImageNameSubstitutor`).

Please see [the documentation on configuration mechanisms](./configuration.md) for more information.

Also, you can use the `ServiceLoader` mechanism to provide the fully qualified class name of the `ImageNameSubstitutor` implementation:

=== "`src/test/resources/META-INF/services/org.testcontainers.utility.ImageNameSubstitutor`"
    ```text
    com.mycompany.testcontainers.ExampleImageNameSubstitutor
    ```


## Overriding image names individually in configuration

!!! note
    This approach is discouraged and deprecated, but is documented for completeness.
    Please consider one of the other approaches outlined in this page instead.
    Overriding individual image names via configuration may be removed in the future. 

Consider this if:

* You have many references to image names in code and changing them is impractical, and
* None of the other options are practical for you

In this case, image name references in code are left **unchanged**.
i.e. you would leave as-is:

<!--codeinclude--> 
[Unchanged direct Docker Hub image name](../examples/junit4/generic/src/test/java/generic/ImageNameSubstitutionTest.java) inside_block:directDockerHubReference
<!--/codeinclude-->

You can force Testcontainers to substitute in a different image [using a configuration file](./configuration.md), which allows some (but not all) container names to be substituted. 
