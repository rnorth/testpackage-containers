package org.testcontainers.jdbc.mariadb;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.jdbc.AbstractJDBCDriverTest;

import java.util.Arrays;
import java.util.EnumSet;

@RunWith(Parameterized.class)
public class MariaDBJDBCDriverTest extends AbstractJDBCDriverTest {

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
            new Object[][] {
                { "jdbc:tc:mariadb://hostname/databasename", EnumSet.noneOf(Options.class) },
                {
                    "jdbc:tc:mariadb://hostname/databasename?user=someuser&TC_INITSCRIPT=somepath/init_mariadb.sql",
                    EnumSet.of(Options.ScriptedSchema, Options.JDBCParams),
                },
                { "jdbc:tc:mariadb:10.11.10://hostname/databasename", EnumSet.noneOf(Options.class) },
                {
                    "jdbc:tc:mariadb:10.11.10://hostname/databasename?TC_INITSCRIPT=somepath/init_unicode_mariadb.sql&useUnicode=yes&characterEncoding=utf8",
                    EnumSet.of(Options.CharacterSet),
                },
                {
                    "jdbc:tc:mariadb:10.11.10://hostname/databasename?user=someuser&TC_INITSCRIPT=somepath/init_mariadb.sql",
                    EnumSet.of(Options.ScriptedSchema, Options.JDBCParams),
                },
                {
                    "jdbc:tc:mariadb:10.11.10://hostname/databasename?user=someuser&TC_INITFUNCTION=org.testcontainers.jdbc.AbstractJDBCDriverTest::sampleInitFunction",
                    EnumSet.of(Options.ScriptedSchema, Options.JDBCParams),
                },
                {
                    "jdbc:tc:mariadb:10.11.10://hostname/databasename?user=someuser&password=somepwd&TC_INITSCRIPT=somepath/init_mariadb.sql",
                    EnumSet.of(Options.ScriptedSchema, Options.JDBCParams),
                },
                {
                    "jdbc:tc:mariadb:10.11.10://hostname/databasename?user=someuser&password=somepwd&TC_INITFUNCTION=org.testcontainers.jdbc.AbstractJDBCDriverTest::sampleInitFunction",
                    EnumSet.of(Options.ScriptedSchema, Options.JDBCParams),
                },
                {
                    "jdbc:tc:mariadb:10.11.10://hostname/databasename?TC_MY_CNF=somepath/mariadb_conf_override",
                    EnumSet.of(Options.CustomIniFile),
                },
            }
        );
    }
}
