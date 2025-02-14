package org.testcontainers.utility;

import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * An {@link ImageNameSubstitutor} which applies a prefix to all image names, e.g. a private registry host and path.
 * The prefix may be set via an environment variable (<code>TESTCONTAINERS_HUB_IMAGE_NAME_PREFIX</code>) or an equivalent
 * configuration file entry (see {@link TestcontainersConfiguration}).
 */
@NoArgsConstructor
@Slf4j
final class PrefixingImageNameSubstitutor extends ImageNameSubstitutor {

    @VisibleForTesting
    static final String PREFIX_PROPERTY_KEY = "hub.image.name.prefix";

    @VisibleForTesting
    static final String NORMALIZE_PROPERTY_KEY = "hub.image.name.normalize";

    private TestcontainersConfiguration configuration = TestcontainersConfiguration.getInstance();

    @VisibleForTesting
    PrefixingImageNameSubstitutor(final TestcontainersConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public DockerImageName apply(DockerImageName original) {
        final String configuredPrefix = configuration.getEnvVarOrProperty(PREFIX_PROPERTY_KEY, "");
        final boolean normalize = Boolean.parseBoolean(
            configuration.getEnvVarOrProperty(NORMALIZE_PROPERTY_KEY, "false")
        );

        if (configuredPrefix.isEmpty()) {
            log.debug("No prefix is configured");
            return original;
        }

        boolean isAHubImage = original.getRegistry().isEmpty();
        if (!isAHubImage) {
            log.debug("Image {} is not a Docker Hub image - not applying registry/repository change", original);
            return original;
        }

        log.debug(
            "Applying changes to image name {}: applying prefix '{}' with normalization: {}",
            original,
            configuredPrefix,
            normalize
        );

        DockerImageName prefixAsImage = DockerImageName.parse(configuredPrefix);

        String repository = original.getRepository();
        if (normalize && !repository.contains("/")) {
            repository = DockerImageName.LIBRARY_PREFIX + repository;
        }

        DockerImageName substituted = original
            .withRegistry(prefixAsImage.getRegistry())
            .withRepository(prefixAsImage.getRepository() + repository);
        if (normalize) {
            substituted = substituted.asCompatibleSubstituteFor(original);
        }
        return substituted;
    }

    @Override
    protected String getDescription() {
        return getClass().getSimpleName();
    }
}
