package org.testcontainers.containers;

import com.github.dockerjava.api.command.CreateNetworkCmd;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.Delegate;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

public interface Network extends AutoCloseable, TestRule {
    Network SHARED = new NetworkImpl(false, null, Collections.emptySet(), null) {
        @Override
        public void close() {
            // Do not allow users to close SHARED network, only ResourceReaper is allowed to close (destroy) it
        }
    };

    String getId();

    @Override
    void close();

    static Network newNetwork() {
        return builder().build();
    }

    static NetworkImpl.NetworkImplBuilder builder() {
        return NetworkImpl.builder();
    }

    @Getter
    class NetworkImpl extends ExternalResource implements Network {

        @Delegate
        private final ContainerNetwork network;

        @Builder
        public NetworkImpl(
            Boolean enableIpv6,
            String driver,
            @Singular Set<Consumer<CreateNetworkCmd>> createNetworkCmdModifiers,
            @Deprecated String id
        ) {
            this.network =
                ContainerNetwork
                    .builder()
                    .enableIpv6(enableIpv6)
                    .driver(driver)
                    .createNetworkCmdModifiers(createNetworkCmdModifiers)
                    .build();
        }

        @Override
        protected void after() {
            this.network.close();
        }
    }
}
