# JDBC support

You can obtain a temporary database in one of two ways:

 * **Using a specially modified JDBC URL**: after making a very simple modification to your system's JDBC URL string, Testcontainers will provide a disposable stand-in database that can be used without requiring modification to your application code.
 * **JUnit @Rule/@ClassRule**: this mode starts a database inside a container before your tests and tears it down afterwards.

## Database containers launched via JDBC URL scheme

As long as you have Testcontainers and the appropriate JDBC driver on your classpath, you can simply modify regular JDBC connection URLs to get a fresh containerized instance of the database each time your application starts up.

_N.B:_

* _TC needs to be on your application's classpath at runtime for this to work_
* _For Spring Boot (Before version `2.3.0`) you need to specify the driver manually `spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver`_

**Original URL**: `jdbc:mysql://localhost:3306/databasename`

Insert `tc:` after `jdbc:` as follows. Note that the hostname, port and database name will be ignored; you can leave these as-is or set them to any value.

!!! note
    We will use `///` (host-less URIs) from now on to emphasis the unimportance of the `host:port` pair.  
    From Testcontainers' perspective, `jdbc:mysql:8.0.36://localhost:3306/databasename` and `jdbc:mysql:8.0.36:///databasename` is the same URI.

!!! warning
    If you're using the JDBC URL support, there is no need to instantiate an instance of the container - Testcontainers will do it automagically.

### JDBC URL examples

#### Using ClickHouse

`jdbc:tc:clickhouse:18.10.3:///databasename`

#### Using CockroachDB

`jdbc:tc:cockroach:v21.2.3:///databasename`

#### Using CrateDB

`jdbc:tc:cratedb:5.2.3//localhost:5432/crate`

#### Using DB2

`jdbc:tc:db2:11.5.0.0a//localhost:5432/crate`

#### Using MariaDB

`jdbc:tc:mariadb:10.3.39:///databasename`

#### Using MySQL

`jdbc:tc:mysql:8.0.36:///databasename`

#### Using MSSQL Server

`jdbc:tc:sqlserver:2017-CU12:///databasename`

#### Using OceanBase

`jdbc:tc:oceanbasece:4.2.1-lts:///databasename`

#### Using Oracle

`jdbc:tc:oracle:21-slim-faststart:///databasename`

#### Using PostGIS

`jdbc:tc:postgis:9.6-2.5:///databasename`

#### Using PostgreSQL

`jdbc:tc:postgresql:9.6.8:///databasename`

#### Using QuestDB

`jdbc:tc:questdb:6.5.3:///databasename`

#### Using TimescaleDB

`jdbc:tc:timescaledb:2.1.0-pg13:///databasename`

#### Using PGVector

`jdbc:tc:pgvector:pg16:///databasename`

#### Using TiDB

`jdbc:tc:tidb:v6.1.0:///databasename`

#### Using Timeplus

`jdbc:tc:timeplus:2.3.21:///databasename`

#### Using Trino

`jdbc:tc:trino:352://localhost/memory/default`

#### Using YugabyteDB

`jdbc:tc:yugabyte:2.14.4.0-b26:///databasename`


### Using a classpath init script

Testcontainers can run an init script after the database container is started, but before your code is given a connection to it. The script must be on the classpath, and is referenced as follows:

`jdbc:tc:mysql:8.0.36:///databasename?TC_INITSCRIPT=somepath/init_mysql.sql`

This is useful if you have a fixed script for setting up database schema, etc.

### Using an init script from a file

If the init script path is prefixed `file:`, it will be loaded from a file (relative to the working directory, which will usually be the project root).

`jdbc:tc:mysql:8.0.36:///databasename?TC_INITSCRIPT=file:src/main/resources/init_mysql.sql`

### Using an init function

Instead of running a fixed script for DB setup, it may be useful to call a Java function that you define. This is intended to allow you to trigger database schema migration tools. To do this, add TC_INITFUNCTION to the URL as follows, passing a full path to the class name and method:

 `jdbc:tc:mysql:8.0.36:///databasename?TC_INITFUNCTION=org.testcontainers.jdbc.JDBCDriverTest::sampleInitFunction`

The init function must be a public static method which takes a `java.sql.Connection` as its only parameter, e.g.
```java
public class JDBCDriverTest {
    public static void sampleInitFunction(Connection connection) throws SQLException {
        // e.g. run schema setup or Flyway/liquibase/etc DB migrations here...
    }
    ...
```

### Running container in daemon mode

By default database container is being stopped as soon as last connection is closed. There are cases when you might need to start container and keep it running till you stop it explicitly or JVM is shutdown. To do this, add `TC_DAEMON` parameter to the URL as follows:

 `jdbc:tc:mysql:8.0.36:///databasename?TC_DAEMON=true`

With this parameter database container will keep running even when there's no open connections.


### Running container with tmpfs options

Container can have `tmpfs` mounts for storing data in host memory. This is useful if you want to speed up your database tests. Be aware that the data will be lost when the container stops.

To pass this option to the container, add `TC_TMPFS` parameter to the URL as follows:

  `jdbc:tc:postgresql:9.6.8:///databasename?TC_TMPFS=/testtmpfs:rw`

If you need more than one option, separate them by comma (e.g. `TC_TMPFS=key:value,key1:value1&other_parameters=foo`).

For more information about `tmpfs` mount, see [the official Docker documentation](https://docs.docker.com/storage/tmpfs/).

## Database container objects

In case you can't use the URL support, or need to fine-tune the container, you can instantiate it yourself.

Add a @Rule or @ClassRule to your test class, e.g.:

```java
public class SimpleMySQLTest {
    @Rule
    public MySQLContainer mysql = new MySQLContainer();
```

Now, in your test code (or a suitable setup method), you can obtain details necessary to connect to this database:

 * `mysql.getJdbcUrl()` provides a JDBC URL your code can connect to
 * `mysql.getUsername()` provides the username your code should pass to the driver
 * `mysql.getPassword()` provides the password your code should pass to the driver

Note that if you use `@Rule`, you will be given an isolated container for each test method. If you use `@ClassRule`, you will get on isolated container for all the methods in the test class.

Examples/Tests:

 * [MySQL](https://github.com/testcontainers/testcontainers-java/blob/main/modules/mysql/src/test/java/org/testcontainers/junit/mysql/SimpleMySQLTest.java)
 * [PostgreSQL](https://github.com/testcontainers/testcontainers-java/blob/main/modules/postgresql/src/test/java/org/testcontainers/junit/postgresql/SimplePostgreSQLTest.java)
