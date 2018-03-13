package org.testcontainers.jdbc;

import static java.util.Arrays.asList;
import static org.rnorth.visibleassertions.VisibleAssertions.assertEquals;

import java.sql.SQLException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

/**
 * This Test class validates that all supported JDBC URL's can be parsed by ConnectionUrl class.
 *
 * @author ManikMagar
 */
@RunWith(Parameterized.class)
public class ConnectionUrlDriversTests {

    @Parameter
    public String jdbcUrl;
    @Parameter(1)
    public String databaseType;
    @Parameter(2)
    public String tag;
    @Parameter(3)
    public String dbHostString;
    @Parameter(4)
    public String databaseName;

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Iterable<Object[]> data() {
        return asList(
            new Object[][]{
                {"jdbc:tc:mysql:5.5.43://hostname/test", "mysql", "5.5.43", "hostname/test", "test"},
                {"jdbc:tc:mysql://hostname/test", "mysql", "latest", "hostname/test", "test"},
                {"jdbc:tc:postgresql:1.2.3://hostname/test", "postgresql", "1.2.3", "hostname/test", "test"},
                {"jdbc:tc:postgresql://hostname/test", "postgresql", "latest", "hostname/test", "test"},
                {"jdbc:tc:sqlserver:1.2.3://localhost;instance=SQLEXPRESS:1433;databaseName=test", "sqlserver", "1.2.3", "localhost;instance=SQLEXPRESS:1433;databaseName=test", ""},
                {"jdbc:tc:sqlserver://localhost;instance=SQLEXPRESS:1433;databaseName=test", "sqlserver", "latest", "localhost;instance=SQLEXPRESS:1433;databaseName=test", ""},
                {"jdbc:tc:mariadb:1.2.3://localhost:3306/test", "mariadb", "1.2.3", "localhost:3306/test", "test"},
                {"jdbc:tc:mariadb://localhost:3306/test", "mariadb", "latest", "localhost:3306/test", "test"},
                {"jdbc:tc:oracle:1.2.3:thin:@localhost:1521/test", "oracle", "1.2.3", "localhost:1521/test", "test"},
                {"jdbc:tc:oracle:thin:@localhost:1521/test", "oracle", "latest", "localhost:1521/test", "test"}
            });
    }

    @Test
    public void test() throws SQLException {
        ConnectionUrl url = new ConnectionUrl(jdbcUrl);
        url.parseUrl();
        assertEquals("Database Type is as expected", databaseType, url.getDatabaseType());
        assertEquals("Image tag is as expected", tag, url.getImageTag());
        assertEquals("Database Host String is as expected", dbHostString, url.getDbHostString());
        assertEquals("Database Name is as expected", databaseName, url.getDatabaseName().orElse(""));
    }
}
