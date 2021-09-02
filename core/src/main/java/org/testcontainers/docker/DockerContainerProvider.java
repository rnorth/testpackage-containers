package org.testcontainers.docker;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.controller.ContainerController;
import org.testcontainers.controller.ContainerProvider;
import org.testcontainers.utility.Base58;

@Slf4j
public class DockerContainerProvider implements ContainerProvider {

    private static final String PROVIDER_IDENTIFIER = "docker";

    @Override
    public ContainerController lazyController() {
        return new DockerContainerController(DockerClientFactory.lazyClient());
    }

    @Override
    public ContainerController controller() {
        return new DockerContainerController(DockerClientFactory.instance().client());
    }

    @Override
    public String exposedPortsIpAddress() {
        return DockerClientFactory.instance().dockerHostIpAddress();
    }

    @Override
    public boolean supportsExecution() {
        String executionDriver = DockerClientFactory.instance().getActiveExecutionDriver();

        // Could be null starting from Docker 1.13
        return executionDriver == null || !executionDriver.startsWith("lxc");
    }

    @Override
    public boolean isFileMountingSupported() {
        return DockerClientFactory.instance().isFileMountingSupported();
    }

    @Override
    public String getRandomImageName() {
        return "localhost/testcontainers/" + Base58.randomString(16).toLowerCase();
    }

    @Override
    public String getIdentifier() {
        return PROVIDER_IDENTIFIER;
    }

    @Override
    public boolean isAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
