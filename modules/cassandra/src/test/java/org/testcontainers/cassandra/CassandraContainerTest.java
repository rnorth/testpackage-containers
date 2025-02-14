package org.testcontainers.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.config.ProgrammaticDriverConfigLoaderBuilder;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.session.ProgrammaticArguments;
import com.datastax.oss.driver.internal.core.context.DefaultDriverContext;
import com.datastax.oss.driver.internal.core.ssl.DefaultSslEngineFactory;
import org.junit.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.utility.DockerImageName;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class CassandraContainerTest {

    private static final String CASSANDRA_IMAGE = "cassandra:3.11.15";

    private static final String TEST_CLUSTER_NAME_IN_CONF = "Test Cluster Integration Test";

    private static final String BASIC_QUERY = "SELECT release_version FROM system.local";

    @Test
    public void testSimple() {
        try ( // container-definition {
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
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

    @Test
    public void testWithSslClientConfig() {
        /*
        Commands executed to generate certificates in 'cassandra-ssl-configuration' directory:
        keytool -genkey -keyalg RSA -validity 36500 -alias localhost -keystore keystore.p12 -storepass cassandra \
            -keypass cassandra -dname "CN=localhost, OU=Testcontainers, O=Testcontainers, L=None, C=None"
        keytool -export -alias localhost -file cassandra.cer -keystore keystore.p12
        keytool -import -v -trustcacerts -alias localhost -file cassandra.cer -keystore truststore.p12

        Commands executed to generate the client certificate and key in 'client-ssl' directory:
        keytool -importkeystore -srckeystore keystore.p12 -destkeystore test_node.p12 -deststoretype PKCS12 \
            -srcstorepass cassandra -deststorepass cassandra
        openssl pkcs12 -in test_node.p12 -nokeys -out cassandra.cer.pem -passin pass:cassandra
        openssl pkcs12 -in test_node.p12 -nodes -nocerts -out cassandra.key.pem -passin pass:cassandra

        Reference:
        https://docs.datastax.com/en/cassandra-oss/3.x/cassandra/configuration/secureSSLCertificates.html
        https://docs.datastax.com/en/cassandra-oss/3.x/cassandra/configuration/secureCqlshSSL.html
        */
        try (
            // with-ssl-config {
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withConfigurationOverride("cassandra-ssl-configuration")
                .withSsl("client-ssl/cassandra.cer.pem", "client-ssl/cassandra.key.pem")
            // }
        ) {
            cassandraContainer.start();
            try {
                ResultSet resultSet = performQueryWithSslClientConfig(cassandraContainer,
                    "SELECT cluster_name FROM system.local");
                assertThat(resultSet.wasApplied()).as("Query was applied").isTrue();
                assertThat(resultSet.one().getString(0))
                    .as("Cassandra configuration is configured with secured connection")
                    .isEqualTo(TEST_CLUSTER_NAME_IN_CONF);
            } catch (Exception e) {
                fail(e);
            }
        }
    }

    @Test
    public void testSimpleSslCqlsh() {
        try (
            CassandraContainer cassandraContainer = new CassandraContainer(CASSANDRA_IMAGE)
                .withConfigurationOverride("cassandra-ssl-configuration")
                .withSsl("client-ssl/cassandra.cer.pem", "client-ssl/cassandra.key.pem")
        ) {
            cassandraContainer.start();

            Container.ExecResult execResult = cassandraContainer.execInContainer(
                "cqlsh",
                "--ssl",
                "-e",
                "SELECT * FROM system_schema.keyspaces;"
            );
            assertThat(execResult.getStdout()).contains("keyspace_name");
        } catch (Exception e) {
            fail(e);
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

    private ResultSet performQueryWithSslClientConfig(CassandraContainer cassandraContainer,
                                                      String cql) {
        final ProgrammaticDriverConfigLoaderBuilder driverConfigLoaderBuilder =
            DriverConfigLoader.programmaticBuilder();
        driverConfigLoaderBuilder.withBoolean(DefaultDriverOption.SSL_HOSTNAME_VALIDATION, false);
        final URL trustStoreUrl = this.getClass().getClassLoader()
            .getResource("cassandra-ssl-configuration/truststore.p12");
        driverConfigLoaderBuilder.withString(DefaultDriverOption.SSL_TRUSTSTORE_PATH, trustStoreUrl.getFile());
        driverConfigLoaderBuilder.withString(DefaultDriverOption.SSL_TRUSTSTORE_PASSWORD, "cassandra");
        final URL keyStoreUrl = this.getClass().getClassLoader()
            .getResource("cassandra-ssl-configuration/keystore.p12");
        driverConfigLoaderBuilder.withString(DefaultDriverOption.SSL_KEYSTORE_PATH, keyStoreUrl.getFile());
        driverConfigLoaderBuilder.withString(DefaultDriverOption.SSL_KEYSTORE_PASSWORD, "cassandra");
        final DriverContext driverContext = new DefaultDriverContext(driverConfigLoaderBuilder.build(),
            ProgrammaticArguments.builder().build());

        final CqlSessionBuilder sessionBuilder = CqlSession.builder();
        final CqlSession cqlSession = sessionBuilder.addContactPoint(cassandraContainer.getContactPoint())
            .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
            .withSslEngineFactory(new DefaultSslEngineFactory(driverContext))
            .build();
        return performQuery(cqlSession, cql);
    }

    private ResultSet performQuery(CqlSession session, String cql) {
        final ResultSet rs = session.execute(cql);
        session.close();
        return rs;
    }
}
