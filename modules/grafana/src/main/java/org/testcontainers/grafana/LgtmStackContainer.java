package org.testcontainers.grafana;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers implementation for Grafana Otel LGTM.
 * <p>
 * Supported image: {@code grafana/otel-lgtm}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Grafana: 3000</li>
 *     <li>Otel Http: 4317</li>
 *     <li>Otel Grpc: 4318</li>
 *     <li>Prometheus: 9090</li>
 * </ul>
 */
public class LgtmStackContainer extends GenericContainer<LgtmStackContainer> {

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("grafana/otel-lgtm");

    private static final int GRAFANA_PORT = 3000;

    private static final int OTLP_GRPC_PORT = 4317;

    private static final int OTLP_HTTP_PORT = 4318;

    private static final int PROMETHEUS_PORT = 9090;

    public LgtmStackContainer(String image) {
        this(DockerImageName.parse(image));
    }

    public LgtmStackContainer(DockerImageName image) {
        super(image);
        image.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        withExposedPorts(GRAFANA_PORT, OTLP_GRPC_PORT, OTLP_HTTP_PORT, PROMETHEUS_PORT);
        waitingFor(
            Wait.forLogMessage(".*The OpenTelemetry collector and the Grafana LGTM stack are up and running.*\\s", 1)
        );
    }

    public String getOtlpGrpcUrl() {
        return "http://" + getHost() + ":" + getMappedPort(OTLP_GRPC_PORT);
    }

    public String getOtlpHttpUrl() {
        return "http://" + getHost() + ":" + getMappedPort(OTLP_HTTP_PORT);
    }

    public String getPromehteusHttpUrl() {
        return "http://" + getHost() + ":" + getMappedPort(PROMETHEUS_PORT);
    }
}
