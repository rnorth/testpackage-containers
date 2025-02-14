package org.testcontainers.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import org.junit.Test;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class CassandraContainerTest {

    private static final String CASSANDRA_IMAGE = "cassandra:3.11.2";

    private static final String TEST_CLUSTER_NAME_IN_CONF = "Test Cluster Integration Test";

    private static final String BASIC_QUERY = "SELECT release_version FROM system.local";

    @Test
    public void testSimple() {
        try ( // container-definition {
            CassandraContainer cassandraContainer = new CassandraContainer("cassandra:3.11.2")
            // }
        ) {
            cassandraContainer.start();
            ResultSet resultSet = performQuery(cassandraContainer, BASIC_QUERY);
            assertThat(resultSet.wasApplied()).as("Query was applied").isTrue();
            assertThat(resultSet.one().getString(0)).as("Result set has release_version").isNotNull();
        }
    }

    @Test
    public void testSpecificVersion() {
        String cassandraVersion = "3.0.15";
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(
                DockerImageName.parse("cassandra").withTag(cassandraVersion)
            )
        ) {
            cassandraContainer.start();
            ResultSet resultSet = performQuery(cassandraContainer, BASIC_QUERY);
            assertThat(resultSet.wasApplied()).as("Query was applied").isTrue();
            assertThat(resultSet.one().getString(0)).as("Cassandra has right version").isEqualTo(cassandraVersion);
        }
    }

    @Test
    public void testConfigurationOverride() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withConfigurationOverride("cassandra-test-configuration-example")
        ) {
            cassandraContainer.start();
            ResultSet resultSet = performQuery(cassandraContainer, "SELECT cluster_name FROM system.local");
            assertThat(resultSet.wasApplied()).as("Query was applied").isTrue();
            assertThat(resultSet.one().getString(0))
                .as("Cassandra configuration is overridden")
                .isEqualTo(TEST_CLUSTER_NAME_IN_CONF);
        }
    }

    @Test(expected = ContainerLaunchException.class)
    public void testEmptyConfigurationOverride() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withConfigurationOverride("cassandra-empty-configuration")
        ) {
            cassandraContainer.start();
        }
    }

    @Test
    public void testInitScript() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withInitScript("initial.cql")
        ) {
            cassandraContainer.start();
            testInitScript(cassandraContainer, false);
        }
    }

    @Test
    public void testNonexistentInitScript() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withInitScript("unknown_script.cql")
        ) {
            assertThat(catchThrowable(cassandraContainer::start))
                .isInstanceOf(ContainerLaunchException.class);
        }
    }

    @Test
    public void testInitScriptWithRequiredAuthentication() {
        try (
            // init-with-auth {
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withConfigurationOverride("cassandra-auth-required-configuration")
                .withInitScript("initial.cql")
            // }
        ) {
            cassandraContainer.start();
            testInitScript(cassandraContainer, true);
        }
    }

    @Test(expected = ContainerLaunchException.class)
    public void testInitScriptWithError() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withInitScript("initial-with-error.cql")
        ) {
            cassandraContainer.start();
        }
    }

    @Test
    public void testInitScriptWithLegacyCassandra() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer("cassandra:2.2.11")
                .withInitScript("initial.cql")
        ) {
            cassandraContainer.start();
            testInitScript(cassandraContainer, false);
        }
    }

    private void testInitScript(CassandraContainer cassandraContainer, boolean withCredentials) {
        String query = "SELECT * FROM keySpaceTest.catalog_category";
        ResultSet resultSet;

        if (withCredentials) {
            resultSet = performQueryWithAuth(cassandraContainer, query);
        } else {
            resultSet = performQuery(cassandraContainer, query);
        }

        assertThat(resultSet.wasApplied()).as("Query was applied").isTrue();
        Row row = resultSet.one();
        assertThat(row.getLong(0)).as("Inserted row is in expected state").isEqualTo(1);
        assertThat(row.getString(1)).as("Inserted row is in expected state").isEqualTo("test_category");
    }

    private ResultSet performQuery(CassandraContainer cassandraContainer, String cql) {
        // cql-session {
        final CqlSession cqlSession = CqlSession
            .builder()
            .addContactPoint(cassandraContainer.getContactPoint())
            .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
            .build();
        // }
        return performQuery(cqlSession, cql);
    }

    private ResultSet performQueryWithAuth(CassandraContainer cassandraContainer, String cql) {
        final CqlSession cqlSession = CqlSession
            .builder()
            .addContactPoint(cassandraContainer.getContactPoint())
            .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
            .withAuthCredentials(cassandraContainer.getUsername(), cassandraContainer.getPassword())
            .build();
        return performQuery(cqlSession, cql);
    }

    private ResultSet performQuery(CqlSession session, String cql) {
        final ResultSet rs = session.execute(cql);
        session.close();
        return rs;
    }
}
