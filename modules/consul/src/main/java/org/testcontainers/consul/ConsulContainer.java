package org.testcontainers.consul;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Capability;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Testcontainers implementation for Consul.
 * <p>
 * Supported images: {@code hashicorp/consul}, {@code consul}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>HTTP: 8500</li>
 *     <li>gRPC: 8502</li>
 * </ul>
 */
public class ConsulContainer extends GenericContainer<ConsulContainer> {

    private static final DockerImageName DEFAULT_OLD_IMAGE_NAME = DockerImageName.parse("consul");

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("hashicorp/consul");

    private static final int CONSUL_HTTP_PORT = 8500;

    private static final int CONSUL_GRPC_PORT = 8502;

    private List<String> initCommands = new ArrayList<>();

    private String[] startConsulCmd = new String[] { "agent", "-dev", "-client", "0.0.0.0" };

    public ConsulContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    public ConsulContainer(final DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_OLD_IMAGE_NAME, DEFAULT_IMAGE_NAME);

        // Use the status leader endpoint to verify if consul is running.
        setWaitStrategy(Wait.forHttp("/v1/status/leader").forPort(CONSUL_HTTP_PORT).forStatusCode(200));

        withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCapAdd(Capability.IPC_LOCK));
        withEnv("CONSUL_ADDR", "http://0.0.0.0:" + CONSUL_HTTP_PORT);
        withCommand(startConsulCmd);
        withExposedPorts(CONSUL_HTTP_PORT, CONSUL_GRPC_PORT);
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        runConsulCommands();
    }

    private void runConsulCommands() {
        if (!initCommands.isEmpty()) {
            String commands = initCommands
                .stream()
                .map(command -> "consul " + command)
                .collect(Collectors.joining(" && "));
            try {
                ExecResult execResult = this.execInContainer(new String[] { "/bin/sh", "-c", commands });
                if (execResult.getExitCode() != 0) {
                    logger()
                        .error(
                            "Failed to execute these init commands {}. Exit code {}. Stdout {}. Stderr {}",
                            initCommands,
                            execResult.getExitCode(),
                            execResult.getStdout(),
                            execResult.getStderr()
                        );
                }
            } catch (IOException | InterruptedException e) {
                logger()
                    .error(
                        "Failed to execute these init commands {}. Exception message: {}",
                        initCommands,
                        e.getMessage()
                    );
            }
        }
    }

    /**
     * Run consul commands using the consul cli.
     *
     * Useful for enabling more secret engines like:
     * <pre>
     *     .withConsulCommand("secrets enable pki")
     *     .withConsulCommand("secrets enable transit")
     * </pre>
     * or register specific K/V like:
     * <pre>
     *     .withConsulCommand("kv put config/testing1 value123")
     * </pre>
     * @param commands The commands to send to the consul cli
     * @return this
     */
    public ConsulContainer withConsulCommand(String... commands) {
        initCommands.addAll(Arrays.asList(commands));
        return self();
    }
}
