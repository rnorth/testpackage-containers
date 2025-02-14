package org.testcontainers.jdbc;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

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
    public void testUrlParsingForRegexPatterns() {
        String urlString = "jdbc:tc:mysql:5.7.34://hostname:3306/databasename?param1=value1&param2=value2";
        ConnectionUrl url = ConnectionUrl.newInstance(urlString);

        // Test database type extraction
        assertThat(url.getDatabaseType()).as("Database Type should be correctly extracted").isEqualTo("mysql");

        // Test image tag extraction
        assertThat(url.getImageTag()).as("Image tag should be present").isPresent();
        assertThat(url.getImageTag().get()).as("Image tag should be correctly extracted").isEqualTo("5.7.34");

        // Test host and port extraction
        assertThat(url.getDatabaseHost()).as("Database host should be present").isPresent();
        assertThat(url.getDatabaseHost().get()).as("Database host should be correctly extracted").isEqualTo("hostname");

        assertThat(url.getDatabasePort()).as("Database port should be present").isPresent();
        assertThat(url.getDatabasePort().get()).as("Database port should be correctly extracted").isEqualTo(3306);

        // Test database name extraction
        assertThat(url.getDatabaseName()).as("Database name should be present").isPresent();
        assertThat(url.getDatabaseName().get()).as("Database name should be correctly extracted").isEqualTo("databasename");

        // Test query parameters extraction
        assertThat(url.getQueryParameters().get("param1")).as("Query parameter 'param1' should be correctly extracted").isEqualTo("value1");
        assertThat(url.getQueryParameters().get("param2")).as("Query parameter 'param2' should be correctly extracted").isEqualTo("value2");
    }

    @Test
    public void testMalformedUrl() {
        String[] malformedUrls = {
            "jdbc:tc:mysql:/hostname:3306/databasename", // Missing '//' after 'mysql:'
            "jdbc:tc://hostname:3306/databasename",     // Missing database type
            "jdbc:tc:mysql://hostname:3306",            // Missing database name
            "jdbc:mysql://hostname:3306/databasename",  // Incorrect prefix 'jdbc:mysql' instead of 'jdbc:tc'
            "jdbc:tc:mysql://:3306/databasename"        // Missing hostname
        };

        for (String urlString : malformedUrls) {
            boolean thrown = false;
            try {
                ConnectionUrl.newInstance(urlString);
            } catch (IllegalArgumentException e) {
                thrown = true;
            }
            assertThat("IllegalArgumentException should be thrown for malformed URL: " + urlString, thrown, equalTo(true));
        }
    }

    @Test
    public void testEdgeCasesInUrlParsing() {
        // URL with an unusually long image tag
        String longTagUrl = "jdbc:tc:mysql:customtag1234567890://hostname:3306/databasename";
        ConnectionUrl longTagUrlObj = ConnectionUrl.newInstance(longTagUrl);
        assertThat(longTagUrlObj.getImageTag()).as("Long image tag should be correctly extracted")
            .contains("customtag1234567890");

        // URL with encoded characters in the database name
        String encodedDbNameUrl = "jdbc:tc:mysql:5.7.34://hostname:3306/data%20base%20name";
        ConnectionUrl encodedDbNameUrlObj = ConnectionUrl.newInstance(encodedDbNameUrl);
        assertThat(encodedDbNameUrlObj.getDatabaseName()).as("Encoded database name should be correctly extracted")
            .contains("data%20base%20name");

        // URL with multiple query parameters with the same name
        String duplicateParamsUrl = "jdbc:tc:mysql://hostname/databasename?param=value1&param=value2";
        ConnectionUrl duplicateParamsUrlObj = ConnectionUrl.newInstance(duplicateParamsUrl);
        assertThat(duplicateParamsUrlObj.getQueryParameters().get("param")).as("Duplicate query parameters should be handled correctly")
            .isEqualTo("value1");

        // URL with special characters in the host name
        String specialCharHostUrl = "jdbc:tc:mysql://host-name_123:3306/databasename";
        ConnectionUrl specialCharHostUrlObj = ConnectionUrl.newInstance(specialCharHostUrl);
        assertThat(specialCharHostUrlObj.getDatabaseHost()).as("Special characters in host name should be correctly extracted")
            .contains("host-name_123");
    }

    @Test
    public void testDaemonModeParsing() {
        String daemonModeUrl = "jdbc:tc:mysql://hostname/databasename?TC_DAEMON=true";
        ConnectionUrl url = ConnectionUrl.newInstance(daemonModeUrl);

        assertThat(url.isInDaemonMode()).as("Daemon mode should be enabled").isTrue();
    }

    @Test
    public void testReusableFlagParsing() {
        String reusableFlagUrl = "jdbc:tc:mysql://hostname/databasename?TC_REUSABLE=true";
        ConnectionUrl url = ConnectionUrl.newInstance(reusableFlagUrl);

        assertThat(url.isReusable()).as("Reusable flag should be enabled").isTrue();
    }


}
