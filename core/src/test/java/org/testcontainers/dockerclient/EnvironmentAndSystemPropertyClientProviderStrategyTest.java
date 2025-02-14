package org.testcontainers.dockerclient;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.transport.SSLConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.testcontainers.utility.MockTestcontainersConfigurationRule;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * Test that we can use Testcontainers configuration file to override settings. We assume that docker-java has test
 * coverage for detection of environment variables (e.g. DOCKER_HOST) and its own properties config file.
 */
@RunWith(MockitoJUnitRunner.class)
public class EnvironmentAndSystemPropertyClientProviderStrategyTest {

    @Rule
    public MockTestcontainersConfigurationRule mockConfig = new MockTestcontainersConfigurationRule();

    private URI defaultDockerHost;

    private com.github.dockerjava.core.SSLConfig defaultSSLConfig;

    @Before
    public void checkEnvironmentClear() {
        // If docker-java picks up non-default settings from the environment, our test needs to know to expect those
        DefaultDockerClientConfig defaultConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        defaultDockerHost = defaultConfig.getDockerHost();
        defaultSSLConfig = defaultConfig.getSSLConfig();
    }

    @Test
    public void testWhenConfigAbsent() {
        Mockito
            .doReturn("auto")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrProperty(eq("dockerconfig.source"), anyString());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.host"), isNull());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.tls.verify"), isNull());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.cert.path"), isNull());

        EnvironmentAndSystemPropertyClientProviderStrategy strategy = new EnvironmentAndSystemPropertyClientProviderStrategy();

        TransportConfig transportConfig = strategy.getTransportConfig();
        assertThat(transportConfig.getDockerHost()).isEqualTo(defaultDockerHost);
        assertThat(transportConfig.getSslConfig()).isEqualTo(defaultSSLConfig);
    }

    @Test
    public void testWhenDockerHostPresent() {
        Mockito
            .doReturn("auto")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrProperty(eq("dockerconfig.source"), anyString());
        Mockito
            .doReturn("tcp://1.2.3.4:2375")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.host"), isNull());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.tls.verify"), isNull());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.cert.path"), isNull());

        EnvironmentAndSystemPropertyClientProviderStrategy strategy = new EnvironmentAndSystemPropertyClientProviderStrategy();

        TransportConfig transportConfig = strategy.getTransportConfig();
        assertThat(transportConfig.getDockerHost().toString()).isEqualTo("tcp://1.2.3.4:2375");
        assertThat(transportConfig.getSslConfig()).isEqualTo(defaultSSLConfig);
    }

    @Test
    public void testWhenDockerSocketPresent() {
        Mockito
            .doReturn("auto")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrProperty(eq("dockerconfig.source"), anyString());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.host"), isNull());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.tls.verify"), isNull());
        Mockito
            .doReturn(null)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.cert.path"), isNull());
        Mockito
            .doReturn("/var/run/docker-alt.sock")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.socket.override"), isNull());

        EnvironmentAndSystemPropertyClientProviderStrategy strategy = new EnvironmentAndSystemPropertyClientProviderStrategy();

        TransportConfig transportConfig = strategy.getTransportConfig();
        assertThat(transportConfig.getDockerHost().toString()).isEqualTo(defaultDockerHost);
        assertThat(transportConfig.getSslConfig()).isEqualTo(defaultSSLConfig);

        String remoteDockerUnixSocketPath = strategy.getRemoteDockerUnixSocketPath();
        assertThat(remoteDockerUnixSocketPath).isEqualTo("/var/run/docker-alt.sock");
    }

    @Test
    public void testWhenDockerHostAndSSLConfigPresent() throws IOException {
        Path tempDir = Files.createTempDirectory("testcontainers-test");
        String tempDirPath = tempDir.toAbsolutePath().toString();

        Mockito
            .doReturn("auto")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrProperty(eq("dockerconfig.source"), anyString());
        Mockito
            .doReturn("tcp://1.2.3.4:2375")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.host"), isNull());
        Mockito
            .doReturn("1")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.tls.verify"), isNull());
        Mockito
            .doReturn(tempDirPath)
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrUserProperty(eq("docker.cert.path"), isNull());

        EnvironmentAndSystemPropertyClientProviderStrategy strategy = new EnvironmentAndSystemPropertyClientProviderStrategy();

        TransportConfig transportConfig = strategy.getTransportConfig();
        assertThat(transportConfig.getDockerHost().toString()).isEqualTo("tcp://1.2.3.4:2375");

        SSLConfig sslConfig = transportConfig.getSslConfig();
        assertThat(sslConfig).extracting("dockerCertPath").isEqualTo(tempDirPath);
    }

    @Test
    public void applicableWhenIgnoringUserPropertiesAndConfigured() {
        Mockito
            .doReturn("autoIgnoringUserProperties")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrProperty(eq("dockerconfig.source"), anyString());

        Properties oldProperties = System.getProperties();
        try {
            System.setProperty("DOCKER_HOST", "tcp://1.2.3.4:2375");
            EnvironmentAndSystemPropertyClientProviderStrategy strategy = new EnvironmentAndSystemPropertyClientProviderStrategy();

            assertThat(strategy.isApplicable()).isTrue();
        } finally {
            System.setProperties(oldProperties);
        }
    }

    @Test
    public void notApplicableWhenIgnoringUserPropertiesAndNotConfigured() {
        assumeThat(System.getenv("DOCKER_HOST")).isNull();

        Mockito
            .doReturn("autoIgnoringUserProperties")
            .when(TestcontainersConfiguration.getInstance())
            .getEnvVarOrProperty(eq("dockerconfig.source"), anyString());

        EnvironmentAndSystemPropertyClientProviderStrategy strategy = new EnvironmentAndSystemPropertyClientProviderStrategy();

        assertThat(strategy.isApplicable()).isFalse();
    }
}
