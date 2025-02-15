package org.testcontainers.utility;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testcontainers.UnstableAPI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Provides a mechanism for fetching configuration/default settings.
 * <p>
 * Configuration may be provided in:
 * <ul>
 *     <li>A file in the user's home directory named <code>.testcontainers.properties</code></li>
 *     <li>A file in the classpath named <code>testcontainers.properties</code></li>
 *     <li>Environment variables</li>
 * </ul>
 * <p>
 * Note that, if using environment variables, property names are in upper case separated by underscores, preceded by
 * <code>TESTCONTAINERS_</code>.
 */
@Data
@Slf4j
public class TestcontainersConfiguration {

    private static String PROPERTIES_FILE_NAME = "testcontainers.properties";

    private static File USER_CONFIG_FILE = new File(System.getProperty("user.home"), "." + PROPERTIES_FILE_NAME);

    private static final String AMBASSADOR_IMAGE = "richnorth/ambassador";

    private static final String SOCAT_IMAGE = "alpine/socat";

    private static final String VNC_RECORDER_IMAGE = "testcontainers/vnc-recorder";

    private static final String COMPOSE_IMAGE = "docker/compose";

    private static final String ALPINE_IMAGE = "alpine";

    private static final String RYUK_IMAGE = "testcontainers/ryuk";

    private static final String KAFKA_IMAGE = "confluentinc/cp-kafka";

    private static final String PULSAR_IMAGE = "apachepulsar/pulsar";

    private static final String LOCALSTACK_IMAGE = "localstack/localstack";

    private static final String SSHD_IMAGE = "testcontainers/sshd";

    private static final String ORACLE_IMAGE = "gvenzl/oracle-xe";

    private static final ImmutableMap<DockerImageName, String> CONTAINER_MAPPING = ImmutableMap
        .<DockerImageName, String>builder()
        .put(DockerImageName.parse(AMBASSADOR_IMAGE), "ambassador.container.image")
        .put(DockerImageName.parse(SOCAT_IMAGE), "socat.container.image")
        .put(DockerImageName.parse(VNC_RECORDER_IMAGE), "vncrecorder.container.image")
        .put(DockerImageName.parse(COMPOSE_IMAGE), "compose.container.image")
        .put(DockerImageName.parse(ALPINE_IMAGE), "tinyimage.container.image")
        .put(DockerImageName.parse(RYUK_IMAGE), "ryuk.container.image")
        .put(DockerImageName.parse(KAFKA_IMAGE), "kafka.container.image")
        .put(DockerImageName.parse(PULSAR_IMAGE), "pulsar.container.image")
        .put(DockerImageName.parse(LOCALSTACK_IMAGE), "localstack.container.image")
        .put(DockerImageName.parse(SSHD_IMAGE), "sshd.container.image")
        .put(DockerImageName.parse(ORACLE_IMAGE), "oracle.container.image")
        .build();

    @Getter(lazy = true)
    private static final TestcontainersConfiguration instance = loadConfiguration();

    @SuppressWarnings({ "ConstantConditions", "unchecked", "rawtypes" })
    @VisibleForTesting
    static AtomicReference<TestcontainersConfiguration> getInstanceField() {
        // Lazy Getter from Lombok changes the field's type to AtomicReference
        return (AtomicReference) (Object) instance;
    }

    private final Properties userProperties;

    private final Properties classpathProperties;

    private final Map<String, String> environment;

    TestcontainersConfiguration(
        Properties userProperties,
        Properties classpathProperties,
        final Map<String, String> environment
    ) {
        this.userProperties = userProperties;
        this.classpathProperties = classpathProperties;
        this.environment = environment;
    }

    @Deprecated
    public String getAmbassadorContainerImage() {
        return getImage(AMBASSADOR_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getSocatContainerImage() {
        return getImage(SOCAT_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getVncRecordedContainerImage() {
        return getImage(VNC_RECORDER_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getDockerComposeContainerImage() {
        return getImage(COMPOSE_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getTinyImage() {
        return getImage(ALPINE_IMAGE).asCanonicalNameString();
    }

    public boolean isRyukPrivileged() {
        return Boolean.parseBoolean(getEnvVarOrProperty("ryuk.container.privileged", "true"));
    }

    @Deprecated
    public String getRyukImage() {
        return getImage(RYUK_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getSSHdImage() {
        return getImage(SSHD_IMAGE).asCanonicalNameString();
    }

    public Integer getRyukTimeout() {
        return Integer.parseInt(getEnvVarOrProperty("ryuk.container.timeout", "30"));
    }

    @Deprecated
    public String getKafkaImage() {
        return getImage(KAFKA_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getOracleImage() {
        return getImage(ORACLE_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getPulsarImage() {
        return getImage(PULSAR_IMAGE).asCanonicalNameString();
    }

    @Deprecated
    public String getLocalStackImage() {
        return getImage(LOCALSTACK_IMAGE).asCanonicalNameString();
    }

    public boolean isDisableChecks() {
        return Boolean.parseBoolean(getEnvVarOrUserProperty("checks.disable", "false"));
    }

    @UnstableAPI
    public boolean environmentSupportsReuse() {
        return Boolean.parseBoolean(getEnvVarOrUserProperty("testcontainers.reuse.enable", "false"));
    }

    public String getDockerClientStrategyClassName() {
        // getConfigurable won't apply the TESTCONTAINERS_ prefix when looking for env vars if DOCKER_ appears at the beginning.
        // Because of this overlap, and the desire to not change this specific TESTCONTAINERS_DOCKER_CLIENT_STRATEGY setting,
        // we special-case the logic here so that docker.client.strategy is used when reading properties files and
        // TESTCONTAINERS_DOCKER_CLIENT_STRATEGY is used when searching environment variables.

        // looks for TESTCONTAINERS_ prefixed env var only
        String prefixedEnvVarStrategy = environment.get("TESTCONTAINERS_DOCKER_CLIENT_STRATEGY");
        if (prefixedEnvVarStrategy != null) {
            return prefixedEnvVarStrategy;
        }

        // looks for unprefixed env var or unprefixed property, or null if the strategy is not set at all
        return getEnvVarOrUserProperty("docker.client.strategy", null);
    }

    public String getTransportType() {
        return getEnvVarOrProperty("transport.type", "httpclient5");
    }

    public Integer getImagePullPauseTimeout() {
        return Integer.parseInt(getEnvVarOrProperty("pull.pause.timeout", "30"));
    }

    public Integer getImagePullTimeout() {
        return Integer.parseInt(getEnvVarOrProperty("pull.timeout", "120"));
    }

    public String getImageSubstitutorClassName() {
        return getEnvVarOrProperty("image.substitutor", null);
    }

    public String getImagePullPolicy() {
        return getEnvVarOrProperty("pull.policy", null);
    }

    public Integer getClientPingTimeout() {
        return Integer.parseInt(getEnvVarOrProperty("client.ping.timeout", "10"));
    }

    @Nullable
    @Contract("_, !null, _ -> !null")
    private String getConfigurable(
        @NotNull final String propertyName,
        @Nullable final String defaultValue,
        Properties... propertiesSources
    ) {
        String envVarName = propertyName.replaceAll("\\.", "_").toUpperCase();
        if (!envVarName.startsWith("TESTCONTAINERS_") && !envVarName.startsWith("DOCKER_")) {
            envVarName = "TESTCONTAINERS_" + envVarName;
        }

        if (environment.containsKey(envVarName)) {
            String value = environment.get(envVarName);
            if (!value.isEmpty()) {
                return value;
            }
        }

        for (final Properties properties : propertiesSources) {
            if (properties.get(propertyName) != null && !properties.get(propertyName).toString().trim().isEmpty()) {
                return (String) properties.get(propertyName);
            }
        }

        return defaultValue;
    }

    /**
     * Gets a configured setting from an environment variable (if present) or a configuration file property otherwise.
     * The configuration file will be the <code>.testcontainers.properties</code> file in the user's home directory or
     * a <code>testcontainers.properties</code> found on the classpath.
     * <p>
     * Note that when searching environment variables, the prefix `TESTCONTAINERS_` will usually be applied to the
     * property name, which will be converted to upper-case with underscore separators. This prefix will not be added
     * if the property name begins `docker.`.
     *
     * @param propertyName name of configuration file property (dot-separated lower case)
     * @return the found value, or null if not set
     */
    @Contract("_, !null -> !null")
    public String getEnvVarOrProperty(@NotNull final String propertyName, @Nullable final String defaultValue) {
        return getConfigurable(propertyName, defaultValue, userProperties, classpathProperties);
    }

    /**
     * Gets a configured setting from an environment variable (if present) or a configuration file property otherwise.
     * The configuration file will be the <code>.testcontainers.properties</code> file in the user's home directory.
     * <p>
     * Note that when searching environment variables, the prefix `TESTCONTAINERS_` will usually be applied to the
     * property name, which will be converted to upper-case with underscore separators. This prefix will not be added
     * if the property name begins `docker.`.
     *
     * @param propertyName name of configuration file property (dot-separated lower case)
     * @return the found value, or null if not set
     */
    @Contract("_, !null -> !null")
    public String getEnvVarOrUserProperty(@NotNull final String propertyName, @Nullable final String defaultValue) {
        return getConfigurable(propertyName, defaultValue, userProperties);
    }

    /**
     * Gets a configured setting from <code>~/.testcontainers.properties</code>.
     *
     * @param propertyName name of configuration file property (dot-separated lower case)
     * @return the found value, or null if not set
     */
    @Contract("_, !null -> !null")
    public String getUserProperty(@NotNull final String propertyName, @Nullable final String defaultValue) {
        return this.userProperties.get(propertyName) != null
            ? (String) this.userProperties.get(propertyName)
            : defaultValue;
    }

    /**
     * @return properties values available from user properties and classpath properties. Values set by environment
     * variable are NOT included.
     * @deprecated usages should be removed ASAP. See {@link TestcontainersConfiguration#getEnvVarOrProperty(String, String)},
     * {@link TestcontainersConfiguration#getEnvVarOrUserProperty(String, String)} or {@link TestcontainersConfiguration#getUserProperty(String, String)}
     * for suitable replacements.
     */
    @Deprecated
    public Properties getProperties() {
        return Stream
            .of(userProperties, classpathProperties)
            .reduce(
                new Properties(),
                (a, b) -> {
                    a.putAll(b);
                    return a;
                }
            );
    }

    @Deprecated
    public boolean updateGlobalConfig(@NonNull String prop, @NonNull String value) {
        return updateUserConfig(prop, value);
    }

    @Synchronized
    public boolean updateUserConfig(@NonNull String prop, @NonNull String value) {
        try {
            if (value.equals(userProperties.get(prop))) {
                return false;
            }

            userProperties.setProperty(prop, value);

            USER_CONFIG_FILE.createNewFile();
            try (OutputStream outputStream = new FileOutputStream(USER_CONFIG_FILE)) {
                userProperties.store(outputStream, "Modified by Testcontainers");
            }

            // Update internal state only if environment config was successfully updated
            userProperties.setProperty(prop, value);
            return true;
        } catch (Exception e) {
            log.debug("Can't store environment property {} in {}", prop, USER_CONFIG_FILE);
            return false;
        }
    }

    @SneakyThrows(MalformedURLException.class)
    private static TestcontainersConfiguration loadConfiguration() {
        return new TestcontainersConfiguration(
            readProperties(USER_CONFIG_FILE.toURI().toURL()),
            ClasspathScanner
                .scanFor(PROPERTIES_FILE_NAME)
                .map(TestcontainersConfiguration::readProperties)
                .reduce(
                    new Properties(),
                    (a, b) -> {
                        // first-write-wins merging - URLs appearing first on the classpath alphabetically will take priority.
                        // Note that this means that file: URLs will always take priority over jar: URLs.
                        b.putAll(a);
                        return b;
                    }
                ),
            System.getenv()
        );
    }

    private static Properties readProperties(URL url) {
        log.debug("Testcontainers configuration overrides will be loaded from {}", url);
        Properties properties = new Properties();
        try (InputStream inputStream = url.openStream()) {
            properties.load(inputStream);
        } catch (FileNotFoundException e) {
            log.debug(
                "Attempted to read Testcontainers configuration file at {} but the file was not found. Exception message: {}",
                url,
                ExceptionUtils.getRootCauseMessage(e)
            );
        } catch (IOException e) {
            log.debug(
                "Attempted to read Testcontainers configuration file at {} but could it not be loaded. Exception message: {}",
                url,
                ExceptionUtils.getRootCauseMessage(e)
            );
        }
        return properties;
    }

    private DockerImageName getImage(final String defaultValue) {
        return getConfiguredSubstituteImage(DockerImageName.parse(defaultValue));
    }

    DockerImageName getConfiguredSubstituteImage(DockerImageName original) {
        for (final Map.Entry<DockerImageName, String> entry : CONTAINER_MAPPING.entrySet()) {
            if (original.isCompatibleWith(entry.getKey())) {
                return Optional
                    .ofNullable(entry.getValue())
                    .map(propertyName -> getEnvVarOrProperty(propertyName, null))
                    .map(String::valueOf)
                    .map(String::trim)
                    .map(DockerImageName::parse)
                    .orElse(original)
                    .asCompatibleSubstituteFor(original);
            }
        }
        return original;
    }
}
