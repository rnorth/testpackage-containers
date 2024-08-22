package org.testcontainers.containers.localstack;

import com.github.dockerjava.api.command.InspectContainerResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.rnorth.ducttape.Preconditions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.ComparableVersion;
import org.testcontainers.utility.DockerImageName;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Testcontainers implementation for LocalStack.
 * <p>
 * Supported images: {@code localstack/localstack}, {@code localstack/localstack-pro}
 * <p>
 * Exposed ports: 4566
 */
@Slf4j
public class LocalStackContainer extends GenericContainer<LocalStackContainer> {

    static final int PORT = 4566;

    @Deprecated
    private static final String HOSTNAME_EXTERNAL_ENV_VAR = "HOSTNAME_EXTERNAL";

    private static final String LOCALSTACK_HOST_ENV_VAR = "LOCALSTACK_HOST";

    private final List<EnabledService> services = new ArrayList<>();

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("localstack/localstack");

    private static final DockerImageName LOCALSTACK_PRO_IMAGE_NAME = DockerImageName.parse("localstack/localstack-pro");

    private static final String DEFAULT_TAG = "0.11.2";

    private static final String DEFAULT_REGION = "us-east-1";

    private static final String DEFAULT_AWS_ACCESS_KEY_ID = "test";

    private static final String DEFAULT_AWS_SECRET_ACCESS_KEY = "test";

    private static final String STARTER_SCRIPT = "/testcontainers_start.sh";

    @Deprecated
    public static final String VERSION = DEFAULT_TAG;

    /**
     * Whether or to assume that all APIs run on different ports (when <code>true</code>) or are
     * exposed on a single port (<code>false</code>). From the Localstack README:
     *
     * <blockquote>Note: Starting with version 0.11.0, all APIs are exposed via a single edge
     * service [...] The API-specific endpoints below are still left for backward-compatibility but
     * may get removed in a future release - please reconfigure your client SDKs to start using the
     * single edge endpoint URL!</blockquote>
     * <p>
     * Testcontainers will use the tag of the docker image to infer whether or not the used version
     * of Localstack supports this feature.
     */
    private final boolean legacyMode;

    /**
     * Starting with version 0.13.0, setting services list on Localstack is not required. When <code>false</code>,
     * containers are started lazily. When <code>true</code>, container fails to start if services list is not provided.
     *
     * Testcontainers will use the tag of the docker image to infer whether or not the used version
     * of Localstack required services list.
     */
    private final boolean servicesEnvVarRequired;

    private final boolean isVersion2;

    /**
     * @deprecated use {@link #LocalStackContainer(DockerImageName)} instead
     */
    @Deprecated
    public LocalStackContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    /**
     * @deprecated use {@link #LocalStackContainer(DockerImageName)} instead
     */
    @Deprecated
    public LocalStackContainer(String version) {
        this(DEFAULT_IMAGE_NAME.withTag(version));
    }

    /**
     * @param dockerImageName    image name to use for Localstack
     */
    public LocalStackContainer(final DockerImageName dockerImageName) {
        this(dockerImageName, shouldRunInLegacyMode(dockerImageName.getVersionPart()));
    }

    /**
     * @param dockerImageName    image name to use for Localstack
     * @param useLegacyMode      if true, each AWS service is exposed on a different port
     * @deprecated use {@link #LocalStackContainer(DockerImageName)} instead
     */
    @Deprecated
    public LocalStackContainer(final DockerImageName dockerImageName, boolean useLegacyMode) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME, LOCALSTACK_PRO_IMAGE_NAME);

        this.legacyMode = useLegacyMode;
        String version = dockerImageName.getVersionPart();
        this.servicesEnvVarRequired = isServicesEnvVarRequired(version);
        this.isVersion2 = isVersion2(version);

        withFileSystemBind(DockerClientFactory.instance().getRemoteDockerUnixSocketPath(), "/var/run/docker.sock");
        waitingFor(Wait.forLogMessage(".*Ready\\.\n", 1));
        withCreateContainerCmdModifier(cmd -> {
            cmd.withEntrypoint(
                "sh",
                "-c",
                "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; " + STARTER_SCRIPT
            );
        });
    }

    private static boolean isVersion2(String version) {
        if (version.equals("latest")) {
            return true;
        }

        ComparableVersion comparableVersion = new ComparableVersion(version);
        return comparableVersion.isGreaterThanOrEqualTo("2.0.0");
    }

    private static boolean isServicesEnvVarRequired(String version) {
        if (version.equals("latest")) {
            return false;
        }

        ComparableVersion comparableVersion = new ComparableVersion(version);
        if (comparableVersion.isSemanticVersion()) {
            return comparableVersion.isLessThan("0.13");
        }

        log.warn("Version {} is not a semantic version, services list is required.", version);

        return true;
    }

    static boolean shouldRunInLegacyMode(String version) {
        // assume that the latest images are up-to-date
        // also consider images with extra packages (like latest-bigdata) and service-specific images (like s3-latest)
        if (version.equals("latest") || version.startsWith("latest-") || version.endsWith("-latest")) {
            return false;
        }

        ComparableVersion comparableVersion = new ComparableVersion(version);
        if (comparableVersion.isSemanticVersion()) {
            boolean versionRequiresLegacyMode = comparableVersion.isLessThan("0.11");
            return versionRequiresLegacyMode;
        }

        log.warn("Version {} is not a semantic version, LocalStack will run in legacy mode.", version);
        log.warn(
            "Consider using \"LocalStackContainer(DockerImageName dockerImageName, boolean legacyMode)\" constructor if you want to disable legacy mode."
        );
        return true;
    }

    @Override
    protected void configure() {
        super.configure();

        if (this.servicesEnvVarRequired) {
            Preconditions.check("services list must not be empty", !services.isEmpty());
        }

        if (!services.isEmpty()) {
            withEnv("SERVICES", services.stream().map(EnabledService::getName).collect(Collectors.joining(",")));
            if (this.servicesEnvVarRequired) {
                withEnv("EAGER_SERVICE_LOADING", "1");
            }
        }

        if (this.isVersion2) {
            resolveHostname(LOCALSTACK_HOST_ENV_VAR);
        } else {
            resolveHostname(HOSTNAME_EXTERNAL_ENV_VAR);
        }

        exposePorts();
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        String command = "#!/bin/bash\n";
        command += "export LAMBDA_DOCKER_FLAGS=" + configureLambdaContainerLabels() + "\n";
        command += "/usr/local/bin/docker-entrypoint.sh\n";
        copyFileToContainer(Transferable.of(command, 0777), STARTER_SCRIPT);
    }

    /**
     * Configure the LocalStack container to include the default testcontainers labels on all spawned lambda containers
     * Necessary to properly clean up lambda containers even if the LocalStack container is killed before it gets the
     * chance.
     * @return the lambda container labels as a string
     */
    private String configureLambdaContainerLabels() {
        String lambdaDockerFlags = internalMarkerLabels();
        Supplier<String> existingLambdaDockerFlagsSupplier = getEnvMap().get("LAMBDA_DOCKER_FLAGS");
        String existingLambdaDockerFlags = existingLambdaDockerFlagsSupplier != null
            ? existingLambdaDockerFlagsSupplier.get()
            : null;
        if (existingLambdaDockerFlags != null) {
            lambdaDockerFlags = existingLambdaDockerFlags + " " + lambdaDockerFlags;
        }
        return "\"" + lambdaDockerFlags + "\"";
    }

    /**
     * Provides a docker argument string including all default labels set on testcontainers containers (excluding reuse labels)
     * @return Argument string in the format `-l key1=value1 -l key2=value2`
     */
    private String internalMarkerLabels() {
        return getContainerInfo()
            .getConfig()
            .getLabels()
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().startsWith(DockerClientFactory.TESTCONTAINERS_LABEL))
            .filter(entry -> {
                return (
                    !entry.getKey().equals("org.testcontainers.hash") &&
                    !entry.getKey().equals("org.testcontainers.copied_files.hash")
                );
            })
            .map(entry -> String.format("-l %s=%s", entry.getKey(), entry.getValue()))
            .collect(Collectors.joining(" "));
    }

    private void resolveHostname(String envVar) {
        String hostnameExternalReason;
        if (getEnvMap().containsKey(envVar)) {
            // do nothing
            hostnameExternalReason = "explicitly as environment variable";
        } else if (getNetwork() != null && getNetworkAliases() != null && getNetworkAliases().size() >= 1) {
            withEnv(envVar, getNetworkAliases().get(getNetworkAliases().size() - 1)); // use the last network alias set
            hostnameExternalReason = "to match last network alias on container with non-default network";
        } else {
            withEnv(envVar, getHost());
            hostnameExternalReason = "to match host-routable address for container";
        }

        logger()
            .info("{} environment variable set to {} ({})", envVar, getEnvMap().get(envVar), hostnameExternalReason);
    }

    private void exposePorts() {
        if (legacyMode) {
            services.stream().map(this::getServicePort).distinct().forEach(this::addExposedPort);
        } else {
            this.addExposedPort(PORT);
        }
    }

    public LocalStackContainer withServices(Service... services) {
        this.services.addAll(Arrays.asList(services));
        return self();
    }

    /**
     * Declare a set of simulated AWS services that should be launched by this container.
     * @param services one or more service names
     * @return this container object
     */
    public LocalStackContainer withServices(EnabledService... services) {
        this.services.addAll(Arrays.asList(services));
        return self();
    }

    public URI getEndpointOverride(Service service) {
        return getEndpointOverride((EnabledService) service);
    }

    /**
     * Provides an endpoint override that is preconfigured to communicate with a given simulated service.
     * The provided endpoint override should be set in the AWS Java SDK v2 when building a client, e.g.:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * <p><strong>Please note that this method is only intended to be used for configuring AWS SDK clients
     * that are running on the test host. If other containers need to call this one, they should be configured
     * specifically to do so using a Docker network and appropriate addressing.</strong></p>
     *
     * @param service the service that is to be accessed
     * @return an {@link URI} endpoint override
     */
    public URI getEndpointOverride(EnabledService service) {
        try {
            final String address = getHost();
            String ipAddress = address;
            // resolve IP address and use that as the endpoint so that path-style access is automatically used for S3
            ipAddress = InetAddress.getByName(address).getHostAddress();
            return new URI("http://" + ipAddress + ":" + getMappedPort(getServicePort(service)));
        } catch (UnknownHostException | URISyntaxException e) {
            throw new IllegalStateException("Cannot obtain endpoint URL", e);
        }
    }

    /**
     * Provides an endpoint to communicate with LocalStack service.
     * The provided endpoint should be set in the AWS Java SDK v2 when building a client, e.g.:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpoint())
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * <p><strong>Please note that this method is only intended to be used for configuring AWS SDK clients
     * that are running on the test host. If other containers need to call this one, they should be configured
     * specifically to do so using a Docker network and appropriate addressing.</strong></p>
     *
     * @return an {@link URI} endpoint
     */
    public URI getEndpoint() {
        try {
            final String address = getHost();
            // resolve IP address and use that as the endpoint so that path-style access is automatically used for S3
            String ipAddress = InetAddress.getByName(address).getHostAddress();
            return new URI("http://" + ipAddress + ":" + getMappedPort(PORT));
        } catch (UnknownHostException | URISyntaxException e) {
            throw new IllegalStateException("Cannot obtain endpoint URL", e);
        }
    }

    private int getServicePort(EnabledService service) {
        return legacyMode ? service.getPort() : PORT;
    }

    /**
     * Provides a default access key that is preconfigured to communicate with a given simulated service.
     * <a href="https://github.com/localstack/localstack/blob/master/doc/interaction/README.md?plain=1#L32">AWS Access Key</a>
     * The access key can be used to construct AWS SDK v2 clients:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * @return a default access key
     */
    public String getAccessKey() {
        return this.getEnvMap().getOrDefault("AWS_ACCESS_KEY_ID", () -> DEFAULT_AWS_ACCESS_KEY_ID).get();
    }

    /**
     * Provides a default secret key that is preconfigured to communicate with a given simulated service.
     * <a href="https://github.com/localstack/localstack/blob/master/doc/interaction/README.md?plain=1#L32">AWS Secret Key</a>
     * The secret key can be used to construct AWS SDK v2 clients:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * @return a default secret key
     */
    public String getSecretKey() {
        return this.getEnvMap().getOrDefault("AWS_SECRET_ACCESS_KEY", () -> DEFAULT_AWS_SECRET_ACCESS_KEY).get();
    }

    /**
     * Provides a default region that is preconfigured to communicate with a given simulated service.
     * The region can be used to construct AWS SDK v2 clients:
     * <pre><code>S3Client s3 = S3Client
     .builder()
     .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
     .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
     localstack.getAccessKey(), localstack.getSecretKey()
     )))
     .region(Region.of(localstack.getRegion()))
     .build()
     </code></pre>
     * @return a default region
     */
    public String getRegion() {
        return this.getEnvMap().getOrDefault("DEFAULT_REGION", () -> DEFAULT_REGION).get();
    }

    public interface EnabledService {
        static EnabledService named(String name) {
            return () -> name;
        }

        String getName();

        default int getPort() {
            return PORT;
        }
    }

    @RequiredArgsConstructor
    @Getter
    @FieldDefaults(makeFinal = true)
    public enum Service implements EnabledService {
        API_GATEWAY("apigateway", 4567),
        EC2("ec2", 4597),
        KINESIS("kinesis", 4568),
        DYNAMODB("dynamodb", 4569),
        DYNAMODB_STREAMS("dynamodbstreams", 4570),
        // TODO: Clarify usage for ELASTICSEARCH and ELASTICSEARCH_SERVICE
        //        ELASTICSEARCH("es",           4571),
        S3("s3", 4572),
        FIREHOSE("firehose", 4573),
        LAMBDA("lambda", 4574),
        SNS("sns", 4575),
        SQS("sqs", 4576),
        REDSHIFT("redshift", 4577),
        //        ELASTICSEARCH_SERVICE("",   4578),
        SES("ses", 4579),
        ROUTE53("route53", 4580),
        CLOUDFORMATION("cloudformation", 4581),
        CLOUDWATCH("cloudwatch", 4582),
        SSM("ssm", 4583),
        SECRETSMANAGER("secretsmanager", 4584),
        STEPFUNCTIONS("stepfunctions", 4585),
        CLOUDWATCHLOGS("logs", 4586),
        STS("sts", 4592),
        IAM("iam", 4593),
        KMS("kms", 4599);

        String localStackName;

        int port;

        @Override
        public String getName() {
            return localStackName;
        }

        @Deprecated
        /*
            Since version 0.11, LocalStack exposes all services on a single (4566) port.
         */
        public int getPort() {
            return port;
        }
    }
}
