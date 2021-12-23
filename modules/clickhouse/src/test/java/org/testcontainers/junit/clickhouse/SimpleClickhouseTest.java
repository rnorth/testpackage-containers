package org.testcontainers.junit.clickhouse;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLConnection;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.junit.Test;
import org.testcontainers.containers.ClickHouseInit;
import org.testcontainers.containers.ClickHouseContainer;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertFalse;

public class SimpleClickhouseTest {

    @Test
    public void testRawMysql() throws Throwable {
        try(ClickHouseContainer clickHouseContainer = new ClickHouseContainer()) {
            clickHouseContainer.start();

            MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setPort(clickHouseContainer.getMappedPort(ClickHouseInit.MYSQL_PORT))
                .setHost(clickHouseContainer.getHost())
                .setDatabase(clickHouseContainer.getDatabaseName())
                .setUser(clickHouseContainer.getUsername())
                .setPassword(clickHouseContainer.getPassword());

            CountDownLatch lock = new CountDownLatch(1);

            Vertx vertx = Vertx.vertx();
            Future<RowSet<Row>> result = MySQLConnection
                .connect(vertx, connectOptions)
                .flatMap(conn -> conn.query("SELECT 1;").execute())
                .onComplete(r -> lock.countDown());

            lock.await();

            assertFalse(result.failed());
        }
    }

}
