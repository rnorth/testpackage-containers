/*
 * Copyright (c) 2020 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.testcontainers.couchbase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import lombok.Cleanup;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Testcontainers implementation for Couchbase.
 * <p>
 * Supported image: {@code couchbase/server}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Console: 8091</li>
 * </ul>
 * <p>
 * Note that it does not depend on a specific couchbase SDK, so it can be used with both the Java SDK 2 and 3 as well
 * as the Scala SDK 1 or newer. We recommend using the latest and greatest SDKs for the best experience.
 */
public class CouchbaseContainer extends GenericContainer<CouchbaseContainer> {

    private static final int MGMT_PORT = 8091;

    private static final int MGMT_SSL_PORT = 18091;

    private static final int VIEW_PORT = 8092;

    private static final int VIEW_SSL_PORT = 18092;

    private static final int QUERY_PORT = 8093;

    private static final int QUERY_SSL_PORT = 18093;

    private static final int SEARCH_PORT = 8094;

    private static final int SEARCH_SSL_PORT = 18094;

    private static final int ANALYTICS_PORT = 8095;

    private static final int ANALYTICS_SSL_PORT = 18095;

    private static final int EVENTING_PORT = 8096;

    private static final int EVENTING_SSL_PORT = 18096;

    private static final int KV_PORT = 11210;

    private static final int KV_SSL_PORT = 11207;

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("couchbase/server");

    private static final String DEFAULT_TAG = "6.5.1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    private String username = "Administrator";

    private String password = "password";

    /**
     * Enabled services does not include Analytics since most users likely do not need to test
     * with it and is also a little heavy on memory and runtime requirements. Also, it is only
     * available with the enterprise edition (EE).
     */
    private Set<CouchbaseService> enabledServices = EnumSet.of(
        CouchbaseService.KV,
        CouchbaseService.QUERY,
        CouchbaseService.SEARCH,
        CouchbaseService.INDEX
    );

    /**
     * Holds the custom service quotas if configured by the user.
     */
    private final Map<CouchbaseService, Integer> customServiceQuotas = new HashMap<>();

    private final List<BucketDefinition> buckets = new ArrayList<>();

    private boolean isEnterprise = false;

    private boolean hasTlsPorts = false;

    /**
     * Creates a new couchbase container with the default image and version.
     * @deprecated use {@link #CouchbaseContainer(DockerImageName)} instead
     */
    @Deprecated
    public CouchbaseContainer() {
        this(DEFAULT_IMAGE_NAME.withTag(DEFAULT_TAG));
    }

    /**
     * Creates a new couchbase container with the specified image name.
     *
     * @param dockerImageName the image name that should be used.
     */
    public CouchbaseContainer(final String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Create a new couchbase container with the specified image name.
     * @param dockerImageName the image name that should be used.
     */
    public CouchbaseContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
    }

    /**
     * Set custom username and password for the admin user.
     *
     * @param username the admin username to use.
     * @param password the password for the admin user.
     * @return this {@link CouchbaseContainer} for chaining purposes.
     */
    public CouchbaseContainer withCredentials(final String username, final String password) {
        checkNotRunning();
        this.username = username;
        this.password = password;
        return this;
    }

    public CouchbaseContainer withBucket(final BucketDefinition bucketDefinition) {
        checkNotRunning();
        this.buckets.add(bucketDefinition);
        return this;
    }

    public CouchbaseContainer withEnabledServices(final CouchbaseService... enabled) {
        checkNotRunning();
        this.enabledServices = EnumSet.copyOf(Arrays.asList(enabled));
        return this;
    }

    /**
     * Configures a custom memory quota for a given service.
     *
     * @param service the service to configure the quota for.
     * @param quotaMb the memory quota in MB.
     * @return this {@link CouchbaseContainer} for chaining purposes.
     */
    public CouchbaseContainer withServiceQuota(final CouchbaseService service, final int quotaMb) {
        checkNotRunning();
        if (!service.hasQuota()) {
            throw new IllegalArgumentException("The provided service (" + service + ") has no quota to configure");
        }
        if (quotaMb < service.getMinimumQuotaMb()) {
            throw new IllegalArgumentException(
                "The custom quota (" +
                quotaMb +
                ") must not be smaller than the " +
                "minimum quota for the service (" +
                service.getMinimumQuotaMb() +
                ")"
            );
        }
        this.customServiceQuotas.put(service, quotaMb);
        return this;
    }

    /**
     * Enables the analytics service which is not enabled by default.
     *
     * @return this {@link CouchbaseContainer} for chaining purposes.
     */
    public CouchbaseContainer withAnalyticsService() {
        checkNotRunning();
        this.enabledServices.add(CouchbaseService.ANALYTICS);
        return this;
    }

    /**
     * Enables the eventing service which is not enabled by default.
     *
     * @return this {@link CouchbaseContainer} for chaining purposes.
     */
    public CouchbaseContainer withEventingService() {
        checkNotRunning();
        this.enabledServices.add(CouchbaseService.EVENTING);
        return this;
    }

    public final String getUsername() {
        return username;
    }

    public final String getPassword() {
        return password;
    }

    public int getBootstrapCarrierDirectPort() {
        return getMappedPort(KV_PORT);
    }

    public int getBootstrapHttpDirectPort() {
        return getMappedPort(MGMT_PORT);
    }

    public String getConnectionString() {
        return String.format("couchbase://%s:%d", getHost(), getBootstrapCarrierDirectPort());
    }

    @Override
    protected void configure() {
        super.configure();

        exposePorts();

        WaitAllStrategy waitStrategy = new WaitAllStrategy();

        // Makes sure that all nodes in the cluster are healthy.
        waitStrategy =
            waitStrategy.withStrategy(
                new HttpWaitStrategy()
                    .forPath("/pools/default")
                    .forPort(MGMT_PORT)
                    .withBasicCredentials(username, password)
                    .forStatusCode(200)
                    .forResponsePredicate(response -> {
                        try {
                            return Optional
                                .of(MAPPER.readTree(response))
                                .map(n -> n.at("/nodes/0/status"))
                                .map(JsonNode::asText)
                                .map("healthy"::equals)
                                .orElse(false);
                        } catch (IOException e) {
                            logger().error("Unable to parse response: {}", response, e);
                            return false;
                        }
                    })
            );

        if (enabledServices.contains(CouchbaseService.QUERY)) {
            waitStrategy =
                waitStrategy.withStrategy(
                    new HttpWaitStrategy()
                        .forPath("/admin/ping")
                        .forPort(QUERY_PORT)
                        .withBasicCredentials(username, password)
                        .forStatusCode(200)
                );
        }

        if (enabledServices.contains(CouchbaseService.ANALYTICS)) {
            waitStrategy =
                waitStrategy.withStrategy(
                    new HttpWaitStrategy()
                        .forPath("/admin/ping")
                        .forPort(ANALYTICS_PORT)
                        .withBasicCredentials(username, password)
                        .forStatusCode(200)
                );
        }

        if (enabledServices.contains(CouchbaseService.EVENTING)) {
            waitStrategy =
                waitStrategy.withStrategy(
                    new HttpWaitStrategy()
                        .forPath("/api/v1/config")
                        .forPort(EVENTING_PORT)
                        .withBasicCredentials(username, password)
                        .forStatusCode(200)
                );
        }

        waitingFor(waitStrategy);
    }

    /**
     * Configures the exposed ports based on the enabled services.
     * <p>
     * Note that the MGMT_PORTs are always enabled since there must always be a cluster
     * manager. Also, the View engine ports are implicitly available on the same nodes
     * where the KV service is enabled - it is not possible to configure them individually.
     */
    private void exposePorts() {
        addExposedPorts(MGMT_PORT, MGMT_SSL_PORT);

        if (enabledServices.contains(CouchbaseService.KV)) {
            addExposedPorts(KV_PORT, KV_SSL_PORT);
            addExposedPorts(VIEW_PORT, VIEW_SSL_PORT);
        }
        if (enabledServices.contains(CouchbaseService.ANALYTICS)) {
            addExposedPorts(ANALYTICS_PORT, ANALYTICS_SSL_PORT);
        }
        if (enabledServices.contains(CouchbaseService.QUERY)) {
            addExposedPorts(QUERY_PORT, QUERY_SSL_PORT);
        }
        if (enabledServices.contains(CouchbaseService.SEARCH)) {
            addExposedPorts(SEARCH_PORT, SEARCH_SSL_PORT);
        }
        if (enabledServices.contains(CouchbaseService.EVENTING)) {
            addExposedPorts(EVENTING_PORT, EVENTING_SSL_PORT);
        }
    }

    @Override
    protected void containerIsStarting(final InspectContainerResponse containerInfo) {
        logger().debug("Couchbase container is starting, performing configuration.");

        timePhase("waitUntilNodeIsOnline", this::waitUntilNodeIsOnline);
        timePhase("initializeIsEnterprise", this::initializeIsEnterprise);
        timePhase("initializeHasTlsPorts", this::initializeHasTlsPorts);
        timePhase("renameNode", this::renameNode);
        timePhase("initializeServices", this::initializeServices);
        timePhase("setMemoryQuotas", this::setMemoryQuotas);
        timePhase("configureAdminUser", this::configureAdminUser);
        timePhase("configureExternalPorts", this::configureExternalPorts);

        if (enabledServices.contains(CouchbaseService.INDEX)) {
            timePhase("configureIndexer", this::configureIndexer);
        }
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        timePhase("createBuckets", this::createBuckets);

        logger()
            .info("Couchbase container is ready! UI available at http://{}:{}", getHost(), getMappedPort(MGMT_PORT));
    }

    /**
     * Before we can start configuring the host, we need to wait until the cluster manager is listening.
     */
    private void waitUntilNodeIsOnline() {
        new HttpWaitStrategy().forPort(MGMT_PORT).forPath("/pools").forStatusCode(200).waitUntilReady(this);
    }

    /**
     * Fetches edition (enterprise or community) of started container.
     */
    private void initializeIsEnterprise() {
        @Cleanup
        Response response = doHttpRequest(MGMT_PORT, "/pools", "GET", null, true);

        try {
            isEnterprise = MAPPER.readTree(response.body().string()).get("isEnterprise").asBoolean();
        } catch (IOException e) {
            throw new IllegalStateException("Couchbase /pools did not return valid JSON");
        }

        if (!isEnterprise) {
            if (enabledServices.contains(CouchbaseService.ANALYTICS)) {
                throw new IllegalStateException("The Analytics Service is only supported with the Enterprise version");
            }
            if (enabledServices.contains(CouchbaseService.EVENTING)) {
                throw new IllegalStateException("The Eventing Service is only supported with the Enterprise version");
            }
        }
    }

    /**
     * Initializes the {@link #hasTlsPorts} flag.
     * <p>
     * Community Edition might support TLS one happy day, so use a "supports TLS" flag separate from
     * the "enterprise edition" flag.
     */
    private void initializeHasTlsPorts() {
        @Cleanup
        Response response = doHttpRequest(MGMT_PORT, "/pools/default/nodeServices", "GET", null, true);

        try {
            String clusterTopology = response.body().string();
            hasTlsPorts =
                !MAPPER
                    .readTree(clusterTopology)
                    .path("nodesExt")
                    .path(0)
                    .path("services")
                    .path("mgmtSSL")
                    .isMissingNode();
        } catch (IOException e) {
            throw new IllegalStateException("Couchbase /pools/default/nodeServices did not return valid JSON");
        }
    }

    /**
     * Rebinds/renames the internal hostname.
     * <p>
     * To make sure the internal hostname is different from the external (alternate) address and the SDK can pick it
     * up automatically, we bind the internal hostname to the internal IP address.
     */
    private void renameNode() {
        logger().debug("Renaming Couchbase Node from localhost to {}", getHost());

        @Cleanup
        Response response = doHttpRequest(
            MGMT_PORT,
            "/node/controller/rename",
            "POST",
            new FormBody.Builder().add("hostname", getInternalIpAddress()).build(),
            false
        );

        checkSuccessfulResponse(response, "Could not rename couchbase node");
    }

    /**
     * Initializes services based on the configured enabled services.
     */
    private void initializeServices() {
        logger().debug("Initializing couchbase services on host: {}", enabledServices);

        final String services = enabledServices
            .stream()
            .map(CouchbaseService::getIdentifier)
            .collect(Collectors.joining(","));

        @Cleanup
        Response response = doHttpRequest(
            MGMT_PORT,
            "/node/controller/setupServices",
            "POST",
            new FormBody.Builder().add("services", services).build(),
            false
        );

        checkSuccessfulResponse(response, "Could not enable couchbase services");
    }

    /**
     * Sets the memory quotas for each enabled service.
     * <p>
     * If there is no explicit custom quota defined, the default minimum quota will be used.
     */
    private void setMemoryQuotas() {
        logger().debug("Custom service memory quotas: {}", customServiceQuotas);

        final FormBody.Builder quotaBuilder = new FormBody.Builder();
        for (CouchbaseService service : enabledServices) {
            if (!service.hasQuota()) {
                continue;
            }

            int quota = customServiceQuotas.getOrDefault(service, service.getMinimumQuotaMb());
            if (CouchbaseService.KV.equals(service)) {
                quotaBuilder.add("memoryQuota", Integer.toString(quota));
            } else {
                quotaBuilder.add(service.getIdentifier() + "MemoryQuota", Integer.toString(quota));
            }
        }

        @Cleanup
        Response response = doHttpRequest(MGMT_PORT, "/pools/default", "POST", quotaBuilder.build(), false);

        checkSuccessfulResponse(response, "Could not configure service memory quotas");
    }

    /**
     * Configures the admin user on the couchbase node.
     * <p>
     * After this stage, all subsequent API calls need to have the basic auth header set.
     */
    private void configureAdminUser() {
        logger().debug("Configuring couchbase admin user with username: \"{}\"", username);

        @Cleanup
        Response response = doHttpRequest(
            MGMT_PORT,
            "/settings/web",
            "POST",
            new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("port", Integer.toString(MGMT_PORT))
                .build(),
            false
        );

        checkSuccessfulResponse(response, "Could not configure couchbase admin user");
    }

    /**
     * Configures the external ports for SDK access.
     * <p>
     * Since the internal ports are not accessible from outside the container, this code configures the "external"
     * hostname and services to align with the mapped ports. The SDK will pick it up and then automatically connect
     * to those ports. Note that for all services non-ssl and ssl ports are configured.
     */
    private void configureExternalPorts() {
        logger().debug("Mapping external ports to the alternate address configuration");

        final FormBody.Builder builder = new FormBody.Builder();
        builder.add("hostname", getHost());
        builder.add("mgmt", Integer.toString(getMappedPort(MGMT_PORT)));
        if (hasTlsPorts) {
            builder.add("mgmtSSL", Integer.toString(getMappedPort(MGMT_SSL_PORT)));
        }

        if (enabledServices.contains(CouchbaseService.KV)) {
            builder.add("kv", Integer.toString(getMappedPort(KV_PORT)));
            builder.add("capi", Integer.toString(getMappedPort(VIEW_PORT)));
            if (hasTlsPorts) {
                builder.add("kvSSL", Integer.toString(getMappedPort(KV_SSL_PORT)));
                builder.add("capiSSL", Integer.toString(getMappedPort(VIEW_SSL_PORT)));
            }
        }

        if (enabledServices.contains(CouchbaseService.QUERY)) {
            builder.add("n1ql", Integer.toString(getMappedPort(QUERY_PORT)));
            if (hasTlsPorts) {
                builder.add("n1qlSSL", Integer.toString(getMappedPort(QUERY_SSL_PORT)));
            }
        }

        if (enabledServices.contains(CouchbaseService.SEARCH)) {
            builder.add("fts", Integer.toString(getMappedPort(SEARCH_PORT)));
            if (hasTlsPorts) {
                builder.add("ftsSSL", Integer.toString(getMappedPort(SEARCH_SSL_PORT)));
            }
        }

        if (enabledServices.contains(CouchbaseService.ANALYTICS)) {
            builder.add("cbas", Integer.toString(getMappedPort(ANALYTICS_PORT)));
            if (hasTlsPorts) {
                builder.add("cbasSSL", Integer.toString(getMappedPort(ANALYTICS_SSL_PORT)));
            }
        }

        if (enabledServices.contains(CouchbaseService.EVENTING)) {
            builder.add("eventingAdminPort", Integer.toString(getMappedPort(EVENTING_PORT)));
            if (hasTlsPorts) {
                builder.add("eventingSSL", Integer.toString(getMappedPort(EVENTING_SSL_PORT)));
            }
        }

        @Cleanup
        Response response = doHttpRequest(
            MGMT_PORT,
            "/node/controller/setupAlternateAddresses/external",
            "PUT",
            builder.build(),
            true
        );

        checkSuccessfulResponse(response, "Could not configure external ports");
    }

    /**
     * Configures the indexer service so that indexes can be created later on the bucket.
     */
    private void configureIndexer() {
        logger().debug("Configuring the indexer service");

        @Cleanup
        Response response = doHttpRequest(
            MGMT_PORT,
            "/settings/indexes",
            "POST",
            new FormBody.Builder().add("storageMode", isEnterprise ? "memory_optimized" : "forestdb").build(),
            true
        );

        checkSuccessfulResponse(response, "Could not configure the indexing service");
    }

    /**
     * Based on the user-configured bucket definitions, creating buckets and corresponding indexes if needed.
     */
    private void createBuckets() {
        logger().debug("Creating {} buckets (and corresponding indexes).", buckets.size());

        for (BucketDefinition bucket : buckets) {
            logger().debug("Creating bucket \"{}\"", bucket.getName());

            @Cleanup
            Response response = doHttpRequest(
                MGMT_PORT,
                "/pools/default/buckets",
                "POST",
                new FormBody.Builder()
                    .add("name", bucket.getName())
                    .add("ramQuotaMB", Integer.toString(bucket.getQuota()))
                    .add("flushEnabled", bucket.hasFlushEnabled() ? "1" : "0")
                    .add("replicaNumber", Integer.toString(bucket.getNumReplicas()))
                    .build(),
                true
            );

            checkSuccessfulResponse(response, "Could not create bucket " + bucket.getName());

            timePhase(
                "createBucket:" + bucket.getName() + ":waitForAllServicesEnabled",
                () -> {
                    new HttpWaitStrategy()
                        .forPath("/pools/default/b/" + bucket.getName())
                        .forPort(MGMT_PORT)
                        .withBasicCredentials(username, password)
                        .forStatusCode(200)
                        .forResponsePredicate(new AllServicesEnabledPredicate())
                        .waitUntilReady(this);
                }
            );

            if (enabledServices.contains(CouchbaseService.QUERY)) {
                // If the query service is enabled, make sure that we only proceed if the query engine also
                // knows about the bucket in its metadata configuration.
                timePhase(
                    "createBucket:" + bucket.getName() + ":queryKeyspacePresent",
                    () -> {
                        Unreliables.retryUntilTrue(
                            1,
                            TimeUnit.MINUTES,
                            () -> {
                                @Cleanup
                                Response queryResponse = doHttpRequest(
                                    QUERY_PORT,
                                    "/query/service",
                                    "POST",
                                    new FormBody.Builder()
                                        .add(
                                            "statement",
                                            "SELECT COUNT(*) > 0 as present FROM system:keyspaces WHERE name = \"" +
                                            bucket.getName() +
                                            "\""
                                        )
                                        .build(),
                                    true
                                );

                                String body = queryResponse.body() != null ? queryResponse.body().string() : null;
                                checkSuccessfulResponse(
                                    queryResponse,
                                    "Could not poll query service state for bucket: " + bucket.getName()
                                );

                                return Optional
                                    .of(MAPPER.readTree(body))
                                    .map(n -> n.at("/results/0/present"))
                                    .map(JsonNode::asBoolean)
                                    .orElse(false);
                            }
                        );
                    }
                );
            }

            if (bucket.hasPrimaryIndex()) {
                if (enabledServices.contains(CouchbaseService.QUERY)) {
                    timePhase(
                        "createBucket:" + bucket.getName() + ":createPrimaryIndex",
                        () -> {
                            Unreliables.retryUntilSuccess(
                                1,
                                TimeUnit.MINUTES,
                                () -> {
                                    @Cleanup
                                    Response queryResponse = doHttpRequest(
                                        QUERY_PORT,
                                        "/query/service",
                                        "POST",
                                        new FormBody.Builder()
                                            .add("statement", "CREATE PRIMARY INDEX on `" + bucket.getName() + "`")
                                            .build(),
                                        true
                                    );

                                    try {
                                        checkSuccessfulResponse(
                                            queryResponse,
                                            "Could not create primary index for bucket " + bucket.getName()
                                        );
                                    } catch (IllegalStateException ex) {
                                        // potentially ignore the error, the index will be eventually built.
                                        if (!ex.getMessage().contains("Index creation will be retried in background")) {
                                            throw ex;
                                        }
                                    }
                                    return null;
                                }
                            );
                        }
                    );

                    timePhase(
                        "createBucket:" + bucket.getName() + ":primaryIndexOnline",
                        () -> {
                            Unreliables.retryUntilTrue(
                                1,
                                TimeUnit.MINUTES,
                                () -> {
                                    @Cleanup
                                    Response stateResponse = doHttpRequest(
                                        QUERY_PORT,
                                        "/query/service",
                                        "POST",
                                        new FormBody.Builder()
                                            .add(
                                                "statement",
                                                "SELECT count(*) > 0 AS online FROM system:indexes where keyspace_id = \"" +
                                                bucket.getName() +
                                                "\" and is_primary = true and state = \"online\""
                                            )
                                            .build(),
                                        true
                                    );

                                    String body = stateResponse.body() != null ? stateResponse.body().string() : null;
                                    checkSuccessfulResponse(
                                        stateResponse,
                                        "Could not poll primary index state for bucket: " + bucket.getName()
                                    );

                                    return Optional
                                        .of(MAPPER.readTree(body))
                                        .map(n -> n.at("/results/0/online"))
                                        .map(JsonNode::asBoolean)
                                        .orElse(false);
                                }
                            );
                        }
                    );
                } else {
                    logger()
                        .info(
                            "Primary index creation for bucket {} ignored, since QUERY service is not present.",
                            bucket.getName()
                        );
                }
            }
        }
    }

    /**
     * Helper method to extract the internal IP address based on the network configuration.
     */
    private String getInternalIpAddress() {
        return getContainerInfo()
            .getNetworkSettings()
            .getNetworks()
            .values()
            .stream()
            .findFirst()
            .map(ContainerNetwork::getIpAddress)
            .orElseThrow(() -> new IllegalStateException("No network available to extract the internal IP from!"));
    }

    /**
     * Helper method to check if the response is successful and release the body if needed.
     *
     * @param response the response to check.
     * @param message the message that should be part of the exception of not successful.
     */
    private void checkSuccessfulResponse(final Response response, final String message) {
        if (!response.isSuccessful()) {
            String body = null;
            if (response.body() != null) {
                try {
                    body = response.body().string();
                } catch (IOException e) {
                    logger().debug("Unable to read body of response: {}", response, e);
                }
            }

            throw new IllegalStateException(message + ": " + response + ", body=" + (body == null ? "<null>" : body));
        }
    }

    /**
     * Checks if already running and if so raises an exception to prevent too-late setters.
     */
    private void checkNotRunning() {
        if (isRunning()) {
            throw new IllegalStateException("Setter can only be called before the container is running");
        }
    }

    /**
     * Helper method to perform a request against a couchbase server HTTP endpoint.
     *
     * @param port the (unmapped) original port that should be used.
     * @param path the relative http path.
     * @param method the http method to use.
     * @param body if present, will be part of the payload.
     * @param auth if authentication with the admin user and password should be used.
     * @return the response of the request.
     */
    private Response doHttpRequest(
        final int port,
        final String path,
        final String method,
        final RequestBody body,
        final boolean auth
    ) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                .url("http://" + getHost() + ":" + getMappedPort(port) + path);

            if (auth) {
                requestBuilder = requestBuilder.header("Authorization", Credentials.basic(username, password));
            }

            if (body == null) {
                requestBuilder = requestBuilder.get();
            } else {
                requestBuilder = requestBuilder.method(method, body);
            }

            return HTTP_CLIENT.newCall(requestBuilder.build()).execute();
        } catch (Exception ex) {
            throw new RuntimeException("Could not perform request against couchbase HTTP endpoint ", ex);
        }
    }

    /**
     * Helper method which times an individual phase and logs it for debugging and optimization purposes.
     *
     * @param name the name of the phase.
     * @param toTime the runnable that should be timed.
     */
    private void timePhase(final String name, final Runnable toTime) {
        long start = System.nanoTime();
        toTime.run();
        long end = System.nanoTime();

        logger().debug("Phase {} took {}ms", name, TimeUnit.NANOSECONDS.toMillis(end - start));
    }

    /**
     * In addition to getting a 200, we need to make sure that all services we need are enabled and available on
     * the bucket.
     * <p>
     *  Fixes the issue observed in https://github.com/testcontainers/testcontainers-java/issues/2993
     */
    private class AllServicesEnabledPredicate implements Predicate<String> {

        @Override
        public boolean test(final String rawConfig) {
            try {
                for (JsonNode node : MAPPER.readTree(rawConfig).at("/nodesExt")) {
                    for (CouchbaseService enabledService : enabledServices) {
                        boolean found = false;
                        Iterator<String> fieldNames = node.get("services").fieldNames();
                        while (fieldNames.hasNext()) {
                            if (fieldNames.next().startsWith(enabledService.getIdentifier())) {
                                found = true;
                            }
                        }
                        if (!found) {
                            logger().trace("Service {} not yet part of config, retrying.", enabledService);
                            return false;
                        }
                    }
                }
                return true;
            } catch (IOException ex) {
                logger().error("Unable to parse response: {}", rawConfig, ex);
                return false;
            }
        }
    }
}
