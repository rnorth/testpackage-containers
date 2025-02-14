package org.testcontainers.jdbc;

import lombok.NonNull;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JdbcDatabaseDelegateTest {

    @Test
    public void testLeakedConnections() {
        final JdbcDatabaseContainerStub stub = new JdbcDatabaseContainerStub(DockerImageName.parse("something"));
        try (JdbcDatabaseDelegate delegate = new JdbcDatabaseDelegate(stub, "")) {
            delegate.execute("foo", null, 0, false, false);
        }
        Assert.assertEquals(0, stub.openConnectionsList.size());
    }

    static class JdbcDatabaseContainerStub extends JdbcDatabaseContainer {

        List<Connection> openConnectionsList = new ArrayList<>();

        public JdbcDatabaseContainerStub(@NonNull DockerImageName dockerImageName) {
            super(dockerImageName);
        }

        @Override
        public String getDriverClassName() {
            return null;
        }

        @Override
        public String getJdbcUrl() {
            return null;
        }

        @Override
        public String getUsername() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        protected String getTestQueryString() {
            return null;
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public Connection createConnection(String queryString) throws NoDriverFoundException, SQLException {
            final Connection connection = mock(Connection.class);
            openConnectionsList.add(connection);
            when(connection.createStatement()).thenReturn(mock(Statement.class));
            connection.close();
            Mockito.doAnswer(ignore -> openConnectionsList.remove(connection)).when(connection).close();
            return connection;
        }

        @Override
        protected Logger logger() {
            return mock(Logger.class);
        }

        @Override
        public void setDockerImageName(@NonNull String dockerImageName) {}
    }
}
