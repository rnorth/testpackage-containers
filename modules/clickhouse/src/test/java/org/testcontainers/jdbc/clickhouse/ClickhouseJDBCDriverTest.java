package org.testcontainers.jdbc.clickhouse;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.testcontainers.jdbc.AbstractJDBCDriverTest;

import java.util.EnumSet;

import static java.util.Arrays.asList;

@RunWith(Parameterized.class)
public class ClickhouseJDBCDriverTest extends AbstractJDBCDriverTest {

    @Parameterized.Parameters(name = "{index} - {0}")
    public static Iterable<Object[]> data() {
        return asList(
            new Object[][]{
                {"jdbc:tc:clickhouse://hostname/databasename", EnumSet.of(Options.PmdKnownBroken)},
                //Not testing jdbc:tc:mysql here because the connection pool used by AbstractJDBCDriverTest tries
                //to several things that aren't currently support by the clickhouse-mysql-impl
            });
    }
}
