package org.testcontainers.containers;

import com.github.dockerjava.api.command.CreateNetworkCmd;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.UnstableAPI;
import org.testcontainers.utility.ResourceReaper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@UnstableAPI
@Getter
class ContainerNetwork implements AutoCloseable {

    private final String name = UUID.randomUUID().toString();

    private final Boolean enableIpv6;

    private final String driver;

    @Singular
    private final Set<Consumer<CreateNetworkCmd>> createNetworkCmdModifiers;

    private String id;

    private final AtomicBoolean initialized = new AtomicBoolean();

    @Builder
    public ContainerNetwork(
        Boolean enableIpv6,
        String driver,
        @Singular Set<Consumer<CreateNetworkCmd>> createNetworkCmdModifiers
    ) {
        this.enableIpv6 = enableIpv6;
        this.driver = driver;
        this.createNetworkCmdModifiers = createNetworkCmdModifiers;
    }

    public synchronized String getId() {
        if (this.initialized.compareAndSet(false, true)) {
            boolean success = false;
            try {
                this.id = create();
                success = true;
            } finally {
                if (!success) {
                    this.initialized.set(false);
                }
            }
        }
        return this.id;
    }

    private String create() {
        CreateNetworkCmd createNetworkCmd = DockerClientFactory.instance().client().createNetworkCmd();

        createNetworkCmd.withName(this.name);
        createNetworkCmd.withCheckDuplicate(true);

        if (this.enableIpv6 != null) {
            createNetworkCmd.withEnableIpv6(this.enableIpv6);
        }

        if (this.driver != null) {
            createNetworkCmd.withDriver(this.driver);
        }

        for (Consumer<CreateNetworkCmd> consumer : this.createNetworkCmdModifiers) {
            consumer.accept(createNetworkCmd);
        }

        Map<String, String> labels = createNetworkCmd.getLabels();
        labels = new HashMap<>(labels != null ? labels : Collections.emptyMap());
        labels.putAll(DockerClientFactory.DEFAULT_LABELS);
        //noinspection deprecation
        labels.putAll(ResourceReaper.instance().getLabels());
        createNetworkCmd.withLabels(labels);

        return createNetworkCmd.exec().getId();
    }

    @Override
    public void close() {
        if (this.initialized.getAndSet(false)) {
            ResourceReaper.instance().removeNetworkById(this.id);
        }
    }
}
