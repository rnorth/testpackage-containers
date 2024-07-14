# Presto Module

See [Database containers](./index.md) for documentation and usage that is common to all database container types.

## Usage example

Running Presto as a stand-in for in a test:

```java
public class SomeTest {

    @Rule
    public PrestoContainer presto = new PrestoContainer();
    
    @Test
    public void someTestMethod() {
        String url = presto.getJdbcUrl();

        ... create a connection and run test as normal
```

Presto comes with several catalogs preconfigured. Most useful ones for testing are

* `tpch` catalog using the [Presto TPCH Connector](https://prestosql.io/docs/current/connector/tpch.html).
  This is a read-only catalog that defines standard TPCH schema, so is available for querying without a need
  to create any tables.
* `memory` catalog using the [Presto Memory Connector](https://prestosql.io/docs/current/connector/memory.html).
  This catalog can be used for creating schemas and tables and does not require any storage, as everything
  is stored fully in-memory.

Example test using the `tpch` and `memory` catalogs:

```java
public class SomeTest {
    @Rule
    public PrestoContainer prestoSql = new PrestoContainer();

    @Test
    public void queryMemoryAndTpchConnectors() throws SQLException {
        try (Connection connection = prestoSql.createConnection();
             Statement statement = connection.createStatement()) {
            // Prepare data
            statement.execute("CREATE TABLE memory.default.table_with_array AS SELECT 1 id, ARRAY[1, 42, 2, 42, 4, 42] my_array");

            // Query Presto using newly created table and a builtin connector
            try (ResultSet resultSet = statement.executeQuery("" +
                "SELECT nationkey, element " +
                "FROM tpch.tiny.nation " +
                "JOIN memory.default.table_with_array twa ON nationkey = twa.id " +
                "LEFT JOIN UNNEST(my_array) a(element) ON true " +
                "ORDER BY element OFFSET 1 FETCH NEXT 3 ROWS WITH TIES ")) {
                List<Integer> actualElements = new ArrayList<>();
                while (resultSet.next()) {
                    actualElements.add(resultSet.getInt("element"));
                }
                Assert.assertEquals(Arrays.asList(2, 4, 42, 42, 42), actualElements);
            }
        }
    }
}
```

## Adding this module to your project dependencies

Add the following dependency to your `pom.xml`/`build.gradle` file:

=== "Gradle"
    ```groovy
    testImplementation "org.testcontainers:presto:{{latest_version}}"
    ```
=== "Maven"
    ```xml
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>presto</artifactId>
        <version>{{latest_version}}</version>
        <scope>test</scope>
    </dependency>
    ```

!!! hint
    Adding this Testcontainers library JAR will not automatically add the Presto JDBC driver JAR to your project.
    You should ensure that your project has the Presto JDBC driver as a dependency, if you plan on using it.
    Refer to [Presto project download page](https://prestodb.io/getting-started/) for instructions.


