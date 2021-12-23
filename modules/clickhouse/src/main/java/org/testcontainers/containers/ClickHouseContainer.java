package org.testcontainers.containers;

import com.google.common.collect.Sets;
import org.testcontainers.utility.DockerImageName;
import java.util.Set;

public class ClickHouseContainer extends GenericContainer<ClickHouseContainer> {

    public ClickHouseContainer() {
        this(ClickHouseInit.DEFAULT_TAG);
    }

    public ClickHouseContainer(String tag) {
        this(ClickHouseInit.DEFAULT_IMAGE_NAME.withTag(tag));
    }

    public ClickHouseContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(ClickHouseInit.DEFAULT_IMAGE_NAME);

        ClickHouseInit.init(this);
    }

    @Override
    public Set<Integer> getLivenessCheckPortNumbers() {
        return Sets.newHashSet(ClickHouseInit.HTTP_PORT);
    }

    public String getDatabaseName() {
        return ClickHouseInit.DATABASE_NAME;
    }

    public String getUsername() {
        return ClickHouseInit.USERNAME;
    }

    public String getPassword() {
        return ClickHouseInit.PASSWORD;
    }

}
