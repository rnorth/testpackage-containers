package org.testcontainers.containers;

import org.testcontainers.utility.DockerImageName;

/**
 * Constants used in both YCQL and YSQL APIs
 *
 * @author srinivasa-vasu
 */
public interface YugabyteContainerConstants {

	DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("yugabytedb/yugabyte");

	String DEFAULT_TAG = "2.12.1.0-b41";

	String NAME = "yugabyte";

	Integer YSQL_PORT = 5433;

	Integer YCQL_PORT = 9042;

	Integer MASTER_DASHBOARD_PORT = 7000;

	Integer TSERVER_DASHBOARD_PORT = 9000;

	String JDBC_DRIVER_CLASS = "com.yugabyte.Driver";

	String JDBC_CONNECT_PREFIX = "jdbc:yugabytedb";

	String ENTRYPOINT = "bin/yugabyted start --daemon=false";

	String LOCAL_DC = "datacenter1";

	String USER_PARAM = "user";

	String PASSWORD_PARAM = "password";

	String YSQL_TEST_QUERY = "SELECT 1";

	String YCQL_TEST_QUERY = "SELECT release_version FROM system.local";

}
