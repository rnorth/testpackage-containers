package org.testcontainers.containers;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.UnstableAPI;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.TestcontainersConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@UnstableAPI
@Slf4j
class ContainerDef {

    @Getter
    private RemoteDockerImage image;

    Set<ExposedPort> exposedPorts = new LinkedHashSet<>();

    Set<PortBinding> portBindings = new HashSet<>();

    Map<String, String> labels = new HashMap<>();

    Map<String, Supplier<String>> envVars = new HashMap<>();

    private String[] entrypoint;

    private String[] command = new String[0];

    @Getter
    private Network network;

    Set<String> networkAliases = new LinkedHashSet<>();

    @Getter
    private String networkMode;

    List<Bind> binds = new ArrayList<>();

    @Getter
    private boolean privilegedMode;

    @Getter
    private WaitStrategy waitStrategy = GenericContainer.DEFAULT_WAIT_STRATEGY;

    public ContainerDef() {}

    protected void applyTo(CreateContainerCmd createCommand) {
        HostConfig hostConfig = createCommand.getHostConfig();
        if (hostConfig == null) {
            hostConfig = new HostConfig();
            createCommand.withHostConfig(hostConfig);
        }
        // PortBindings must contain:
        //  * all exposed ports with a randomized host port (equivalent to -p CONTAINER_PORT)
        //  * all exposed ports with a fixed host port (equivalent to -p HOST_PORT:CONTAINER_PORT)
        Map<ExposedPort, PortBinding> allPortBindings = new HashMap<>();
        // First, collect all the randomized host ports from our 'exposedPorts' field
        for (ExposedPort exposedPort : this.exposedPorts) {
            allPortBindings.put(exposedPort, new PortBinding(Ports.Binding.empty(), exposedPort));
        }
        // Next, collect all the fixed host ports from our 'portBindings' field, overwriting any randomized ports so that
        // we don't create two bindings for the same container port.
        for (PortBinding portBinding : this.portBindings) {
            allPortBindings.put(portBinding.getExposedPort(), portBinding);
        }
        hostConfig.withPortBindings(new ArrayList<>(allPortBindings.values()));

        // Next, ExposedPorts must be set up to publish all of the above ports, both randomized and fixed.
        createCommand.withExposedPorts(new ArrayList<>(allPortBindings.keySet()));

        createCommand.withEnv(
            this.envVars.entrySet()
                .stream()
                .filter(it -> it.getValue() != null && it.getValue().get() != null)
                .map(it -> it.getKey() + "=" + it.getValue().get())
                .toArray(String[]::new)
        );

        if (this.entrypoint != null) {
            createCommand.withEntrypoint(this.entrypoint);
        }

        if (this.command != null) {
            createCommand.withCmd(this.command);
        }

        if (this.network != null) {
            hostConfig.withNetworkMode(this.network.getId());
            createCommand.withAliases(this.networkAliases.toArray(new String[0]));
        } else {
            if (this.networkMode != null) {
                createCommand.getHostConfig().withNetworkMode(this.networkMode);
            }
        }

        boolean shouldCheckFileMountingSupport =
            this.binds.size() > 0 && !TestcontainersConfiguration.getInstance().isDisableChecks();
        if (shouldCheckFileMountingSupport) {
            if (!DockerClientFactory.instance().isFileMountingSupported()) {
                log.warn(
                    "Unable to mount a file from test host into a running container. " +
                    "This may be a misconfiguration or limitation of your Docker environment. " +
                    "Some features might not work."
                );
            }
        }

        hostConfig.withBinds(this.binds.toArray(new Bind[0]));

        if (this.privilegedMode) {
            createCommand.getHostConfig().withPrivileged(this.privilegedMode);
        }

        Map<String, String> combinedLabels = new HashMap<>(this.labels);
        if (createCommand.getLabels() != null) {
            combinedLabels.putAll(createCommand.getLabels());
        }

        createCommand.withLabels(combinedLabels);
    }

    protected void setImage(RemoteDockerImage image) {
        this.image = image;
    }

    protected void setImage(String image) {
        setImage(DockerImageName.parse(image));
    }

    protected void setImage(DockerImageName image) {
        setImage(new RemoteDockerImage(image));
    }

    public Set<ExposedPort> getExposedPorts() {
        return new LinkedHashSet<>(this.exposedPorts);
    }

    protected void setExposedPorts(Set<ExposedPort> exposedPorts) {
        this.exposedPorts.clear();
        this.exposedPorts.addAll(exposedPorts);
    }

    protected void addExposedPorts(ExposedPort... exposedPorts) {
        this.exposedPorts.addAll(Arrays.asList(exposedPorts));
    }

    protected void addExposedPort(ExposedPort exposedPort) {
        this.exposedPorts.add(exposedPort);
    }

    protected void setExposedTcpPorts(Set<Integer> ports) {
        this.exposedPorts.clear();
        ports.forEach(port -> this.exposedPorts.add(ExposedPort.tcp(port)));
    }

    protected void addExposedTcpPorts(int... ports) {
        for (int port : ports) {
            this.exposedPorts.add(ExposedPort.tcp(port));
        }
    }

    protected void addExposedTcpPort(int port) {
        this.exposedPorts.add(ExposedPort.tcp(port));
    }

    protected void addExposedPort(int port, InternetProtocol protocol) {
        this.exposedPorts.add(new ExposedPort(port, protocol));
    }

    public Set<PortBinding> getPortBindings() {
        return new HashSet<>(this.portBindings);
    }

    protected void setPortBindings(Set<PortBinding> portBindings) {
        this.portBindings.clear();
        this.portBindings.addAll(portBindings);
    }

    protected void addPortBindings(PortBinding... portBindings) {
        this.portBindings.addAll(Arrays.asList(portBindings));
    }

    protected void addPortBinding(PortBinding portBinding) {
        this.portBindings.add(portBinding);
    }

    public Map<String, String> getLabels() {
        return new HashMap<>(this.labels);
    }

    protected void setLabels(Map<String, String> labels) {
        this.labels.clear();
        this.labels.putAll(labels);
    }

    protected void addLabels(Map<String, String> labels) {
        this.labels.putAll(labels);
    }

    protected void addLabel(String key, String value) {
        this.labels.put(key, value);
    }

    public Map<String, Supplier<String>> getEnvVars() {
        return new HashMap<>(this.envVars);
    }

    protected void setEnvVars(Map<String, Supplier<String>> envVars) {
        this.envVars.clear();
        this.envVars.putAll(envVars);
    }

    protected void addEnvVars(Map<String, String> envVars) {
        this.envVars.putAll(
                envVars
                    .entrySet()
                    .stream()
                    .collect(
                        HashMap::new,
                        (map, entry) -> map.put(entry.getKey(), () -> entry.getValue()),
                        HashMap::putAll
                    )
            );
    }

    protected void addEnvVar(String key, String value) {
        this.envVars.put(key, () -> value);
    }

    public String[] getEntrypoint() {
        return Arrays.copyOf(this.entrypoint, this.entrypoint.length);
    }

    protected void setEntrypoint(String... entrypoint) {
        this.entrypoint = entrypoint;
    }

    public String[] getCommand() {
        return Arrays.copyOf(this.command, this.command.length);
    }

    protected void setCommand(String... command) {
        this.command = command;
    }

    protected void setNetwork(Network network) {
        this.network = network;
    }

    public Set<String> getNetworkAliases() {
        return new LinkedHashSet<>(this.networkAliases);
    }

    protected void setNetworkAliases(Set<String> aliases) {
        this.networkAliases.clear();
        this.networkAliases.addAll(aliases);
    }

    protected void addNetworkAliases(String... aliases) {
        this.networkAliases.addAll(Arrays.asList(aliases));
    }

    protected void addNetworkAlias(String alias) {
        this.networkAliases.add(alias);
    }

    protected void setNetworkMode(String networkMode) {
        this.networkMode = networkMode;
    }

    protected void setPrivilegedMode(boolean privilegedMode) {
        this.privilegedMode = privilegedMode;
    }

    public List<Bind> getBinds() {
        return new ArrayList<>(this.binds);
    }

    protected void setBinds(List<Bind> binds) {
        this.binds.clear();
        this.binds.addAll(binds);
    }

    protected void addBinds(Bind... binds) {
        this.binds.addAll(Arrays.asList(binds));
    }

    protected void addBind(Bind bind) {
        this.binds.add(bind);
    }

    protected void setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }
}
