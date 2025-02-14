package org.testcontainers;

import org.testcontainers.utility.DockerImageName;

public interface MariaDBTestImages {
    DockerImageName MARIADB_IMAGE = DockerImageName.parse("mariadb:10.11.10");
}
