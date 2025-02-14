package org.testcontainers.dockerclient;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import lombok.Getter;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.util.Optional;

/**
 * Use environment variables and system properties (as supported by the underlying DockerClient DefaultConfigBuilder)
 * to try and locate a docker environment based on the currently active configuration.
 * <p>
 * Docker Host resolution order is:
 * <ol>
 *     <li>DOCKER_HOST env var</li>
 *     <li>docker.host in ~/.testcontainers.properties</li>
 *     <li>docker host pointed by the Docker context</li>
 * </ol>
 * <p>
 * Docker context resolution order is
 * <ol>
 *     <li>DOCKER_CONTEXT env var</li>
 *     <li>docker.context in ~/.testcontainers.properties</li>
 *     <li>current docker context pointed by the Docker config</li>
 * </ol>
 * <p>
 * Docker config resolution order is
 * <ol>
 *     <li>DOCKER_CONFIG env var</li>
 *     <li>$HOME/.docker</li>
 * </ol>
 *
 * @deprecated this class is used by the SPI and should not be used directly
 */
@Deprecated
public final class EnvironmentAndSystemPropertyClientProviderStrategy extends DockerClientProviderStrategy {

    public static final int PRIORITY = 100;

    private final DockerClientConfig dockerClientConfig;

    @Getter
    private final boolean applicable;

    public EnvironmentAndSystemPropertyClientProviderStrategy() {
        // use docker-java defaults if present, overridden if our own configuration is set
        this(DefaultDockerClientConfig.createDefaultConfigBuilder());
    }

    EnvironmentAndSystemPropertyClientProviderStrategy(DefaultDockerClientConfig.Builder configBuilder) {
        String dockerConfigSource = TestcontainersConfiguration
            .getInstance()
            .getEnvVarOrProperty("dockerconfig.source", "auto");

        switch (dockerConfigSource) {
            case "auto":
                Optional<String> dockerHost = getSetting("docker.host");
                dockerHost.ifPresent(configBuilder::withDockerHost);
                getSetting("docker.config").ifPresent(configBuilder::withDockerConfig);
                getSetting("docker.context").ifPresent(configBuilder::withDockerContext);
                getSetting("docker.tls.verify").ifPresent(configBuilder::withDockerTlsVerify);
                getSetting("docker.cert.path").ifPresent(configBuilder::withDockerCertPath);
                // We don't have access to the docker-java internals to understand whether a docker-context is defined or not.
                // Therefore, we assume there is always a docker context defined and try it to start with.
                // In case there is an error initializing the docker client with this library, it will fallback to the other resolvers.
                applicable = true;
                break;
            case "autoIgnoringUserProperties":
                applicable = configBuilder.isDockerHostSetExplicitly();
                break;
            default:
                throw new InvalidConfigurationException("Invalid value for dockerconfig.source: " + dockerConfigSource);
        }

        dockerClientConfig = configBuilder.build();
    }

    private Optional<String> getSetting(final String name) {
        return Optional.ofNullable(TestcontainersConfiguration.getInstance().getEnvVarOrUserProperty(name, null));
    }

    @Override
    public TransportConfig getTransportConfig() {
        return TransportConfig
            .builder()
            .dockerHost(dockerClientConfig.getDockerHost())
            .sslConfig(dockerClientConfig.getSSLConfig())
            .build();
    }

    @Override
    protected int getPriority() {
        return PRIORITY;
    }

    @Override
    public String getDescription() {
        return (
            "Environment variables, system properties and defaults. Resolved dockerHost=" +
            dockerClientConfig.getDockerHost()
        );
    }

    @Override
    protected boolean isPersistable() {
        return false;
    }
}
