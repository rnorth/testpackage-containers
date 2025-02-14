package org.testcontainers.jdbc;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionUrlTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testConnectionUrl1() {
        String urlString = "jdbc:tc:mysql:8.0.36://somehostname:3306/databasename?a=b&c=d";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.getDatabaseType()).as("Database Type value is as expected").isEqualTo("mysql");
        assertThat(url.getImageTag()).as("Database Image tag value is as expected").contains("8.0.36");
        assertThat(url.getDbHostString())
            .as("Database Host String is as expected")
            .isEqualTo("somehostname:3306/databasename");
        assertThat(url.getQueryString()).as("Query String value is as expected").contains("?a=b&c=d");
        assertThat(url.getDatabaseHost()).as("Database Host value is as expected").contains("somehostname");
        assertThat(url.getDatabasePort()).as("Database Port value is as expected").contains(3306);
        assertThat(url.getDatabaseName()).as("Database Name value is as expected").contains("databasename");

        assertThat(url.getQueryParameters()).as("Parameter a is captured").containsEntry("a", "b");
        assertThat(url.getQueryParameters()).as("Parameter c is captured").containsEntry("c", "d");
    }

    @Test
    public void testConnectionUrl2() {
        String urlString = "jdbc:tc:mysql://somehostname/databasename";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.getDatabaseType()).as("Database Type value is as expected").isEqualTo("mysql");
        assertThat(url.getImageTag()).as("Database Image tag value is as expected").isNotPresent();
        assertThat(url.getDbHostString())
            .as("Database Host String is as expected")
            .isEqualTo("somehostname/databasename");
        assertThat(url.getQueryString()).as("Query String value is as expected").isEmpty();
        assertThat(url.getDatabaseHost()).as("Database Host value is as expected").contains("somehostname");
        assertThat(url.getDatabasePort()).as("Database Port is null as expected").isNotPresent();
        assertThat(url.getDatabaseName()).as("Database Name value is as expected").contains("databasename");

        assertThat(url.getQueryParameters().isEmpty()).as("Connection Parameters set is empty").isTrue();
    }

    @Test
    public void testEmptyQueryParameter() {
        ConnectionUrl url = ConnectionUrl.newInstance("jdbc:tc:mysql://somehostname/databasename?key=");
        assertThat(url.getQueryParameters().get("key")).as("'key' property value").isEqualTo("");
    }

    @Test
    public void testTmpfsOption() {
        String urlString = "jdbc:tc:mysql://somehostname/databasename?TC_TMPFS=key:value,key1:value1";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.getQueryParameters()).as("Connection Parameters set is empty").isEmpty();
        assertThat(url.getContainerParameters()).as("Container Parameters set is not empty").isNotEmpty();
        assertThat(url.getContainerParameters())
            .as("Container Parameter TC_TMPFS is true")
            .containsEntry("TC_TMPFS", "key:value,key1:value1");
        assertThat(url.getTmpfsOptions()).as("tmpfs option key has correct value").containsEntry("key", "value");
        assertThat(url.getTmpfsOptions()).as("tmpfs option key1 has correct value").containsEntry("key1", "value1");
    }

    @Test
    public void testInitScriptPathCapture() {
        String urlString =
            "jdbc:tc:mysql:8.0.36://somehostname:3306/databasename?a=b&c=d&TC_INITSCRIPT=somepath/init_mysql.sql";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.getInitScriptPath())
            .as("Database Type value is as expected")
            .contains("somepath/init_mysql.sql");
        assertThat(url.getQueryString()).as("Query String value is as expected").contains("?a=b&c=d");
        assertThat(url.getContainerParameters())
            .as("INIT SCRIPT Path exists in Container Parameters")
            .containsEntry("TC_INITSCRIPT", "somepath/init_mysql.sql");

        //Parameter sets are unmodifiable
        thrown.expect(UnsupportedOperationException.class);
        url.getContainerParameters().remove("TC_INITSCRIPT");
        url.getQueryParameters().remove("a");
    }

    @Test
    public void testInitFunctionCapture() {
        String urlString =
            "jdbc:tc:mysql:8.0.36://somehostname:3306/databasename?a=b&c=d&TC_INITFUNCTION=org.testcontainers.jdbc.JDBCDriverTest::sampleInitFunction";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.getInitFunction()).as("Init Function parameter exists").isPresent();

        assertThat(url.getInitFunction().get().getClassName())
            .as("Init function class is as expected")
            .isEqualTo("org.testcontainers.jdbc.JDBCDriverTest");
        assertThat(url.getInitFunction().get().getMethodName())
            .as("Init function class is as expected")
            .isEqualTo("sampleInitFunction");
    }

    @Test
    public void testDaemonCapture() {
        String urlString = "jdbc:tc:mysql:8.0.36://somehostname:3306/databasename?a=b&c=d&TC_DAEMON=true";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.isInDaemonMode()).as("Daemon flag is set to true.").isTrue();
    }

    @Test
    public void testHostLessUrl() {
        String urlString = "jdbc:tc:mysql:///dbname";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        assertThat(url.getDatabaseName()).as("Database Name value is expected").contains("dbname");
    }
}
