package org.testcontainers.cassandra.delegate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.ExecConfig;
import org.testcontainers.delegate.AbstractDatabaseDelegate;
import org.testcontainers.ext.ScriptUtils.ScriptStatementFailedException;

import java.io.IOException;

/**
 * Cassandra database delegate
 */
@Slf4j
@RequiredArgsConstructor
public class CassandraDatabaseDelegate extends AbstractDatabaseDelegate<Void> {

    private final ContainerState container;

    @Override
    protected Void createNewConnection() {
        // Return null here, because we run scripts using cqlsh command directly in the container.
        // So, we don't use connection object in the execute() method.
        return null;
    }

    public void execute(
        String statement,
        String scriptPath,
        int lineNumber,
        boolean continueOnError,
        boolean ignoreFailedDrops,
        boolean silentErrorLogs
    ) {
        try {
            // Use cqlsh command directly inside the container to execute statements
            // See documentation here: https://cassandra.apache.org/doc/stable/cassandra/tools/cqlsh.html
            String[] cqlshCommand = new String[] { "cqlsh" };

            if (this.container instanceof CassandraContainer) {
                CassandraContainer cassandraContainer = (CassandraContainer) this.container;
                String username = cassandraContainer.getUsername();
                String password = cassandraContainer.getPassword();
                if (cassandraContainer.isSslRequired()) {
                   cqlshCommand = ArrayUtils.add(cqlshCommand, "--ssl");
                }
                cqlshCommand = ArrayUtils.addAll(cqlshCommand, "-u", username, "-p", password);
            }

            // If no statement specified, directly execute the script specified into scriptPath (using -f argument),
            // otherwise execute the given statement (using -e argument).
            String executeArg = "-e";
            String executeArgValue = statement;
            if (StringUtils.isBlank(statement)) {
                executeArg = "-f";
                executeArgValue = scriptPath;
            }
            cqlshCommand = ArrayUtils.addAll(cqlshCommand, executeArg, executeArgValue);

            Container.ExecResult result =
                this.container.execInContainer(ExecConfig.builder().command(cqlshCommand).build());
            if (result.getExitCode() == 0) {
                if (StringUtils.isBlank(statement)) {
                    log.info("CQL script {} successfully executed", scriptPath);
                } else {
                    log.info("CQL statement {} was applied", statement);
                }
            } else {
                if (!silentErrorLogs) {
                    log.error("CQL script execution failed with error: \n{}", result.getStderr());
                }
                throw new ScriptStatementFailedException(statement, lineNumber, scriptPath);
            }
        } catch (IOException | InterruptedException e) {
            throw new ScriptStatementFailedException(statement, lineNumber, scriptPath, e);
        }
    }

    @Override
    public void execute(
        String statement,
        String scriptPath,
        int lineNumber,
        boolean continueOnError,
        boolean ignoreFailedDrops
    ) {
        this.execute(statement, scriptPath, lineNumber, continueOnError, ignoreFailedDrops, false);
    }

    @Override
    protected void closeConnectionQuietly(Void session) {
        // Nothing to do here, because we run scripts using cqlsh command directly in the container.
    }
}
