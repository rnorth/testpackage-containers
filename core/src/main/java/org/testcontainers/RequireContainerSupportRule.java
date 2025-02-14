package org.testcontainers;

import org.junit.AssumptionViolatedException;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Junit 4 {@code Rule} that will skip the test if the docker client is not available.
 * This rule must have a lower {@link Rule#order} specified that any other {@code Rule} that
 * uses docker in order to skip tests, otherwise the other rules would fail first causing build failures.
 * e.g. <pre><code>
    {@literal @}Rule(order = -10)
    public RequireContainerSupportRule rcr = new RequireContainerSupportRule();
 }
 * </code></pre>
 */
public class RequireContainerSupportRule implements TestRule {

    @Override
    public Statement apply(Statement base, Description description) {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return base;
        }
        return new DockerNotAvailbleStatement();
    }

    private static class DockerNotAvailbleStatement extends Statement {

        @Override
        public void evaluate() throws Throwable {
            throw new AssumptionViolatedException(
                "Docker support is not available and this test requires TestContainers which needs docker"
            );
        }
    }
}
