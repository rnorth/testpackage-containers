package org.testcontainers;

import org.testcontainers.utility.DockerImageName;

public interface PrestoTestImages {
    DockerImageName PRESTO_TEST_IMAGE = DockerImageName.parse("prestodb/presto:0.290");

    DockerImageName PRESTO_PREVIOUS_VERSION_TEST_IMAGE = DockerImageName.parse("prestodb/presto:0.290");
}
