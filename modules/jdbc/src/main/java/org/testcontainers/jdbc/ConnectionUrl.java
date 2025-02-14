package org.testcontainers.jdbc;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.testcontainers.UnstableAPI;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is an Immutable class holding JDBC Connection Url and its parsed components, used by {@link ContainerDatabaseDriver}.
 * <p>
 * {@link ConnectionUrl#parseUrl()} method must be called after instantiating this class.
 */
@EqualsAndHashCode(of = "url")
@Getter
public class ConnectionUrl {

    private String url;

    private String databaseType;

    private Optional<String> imageTag;

    /**
     * This is a part of the connection string that may specify host:port/databasename.
     * It may vary for different clients and so clients can parse it as needed.
     */
    private String dbHostString;

    private boolean inDaemonMode = false;

    private Optional<String> databaseHost = Optional.empty();

    private Optional<Integer> databasePort = Optional.empty();

    private Optional<String> databaseName = Optional.empty();

    private Optional<String> initScriptPath = Optional.empty();

    @UnstableAPI
    private boolean reusable = false;

    private Optional<InitFunctionDef> initFunction = Optional.empty();

    private Optional<String> queryString;

    private Map<String, String> containerParameters;

    private Map<String, String> queryParameters;

    private Map<String, String> tmpfsOptions = new HashMap<>();

    public static ConnectionUrl newInstance(final String url) {
        ConnectionUrl connectionUrl = new ConnectionUrl(url);
        connectionUrl.parseUrl();
        return connectionUrl;
    }

    private ConnectionUrl(final String url) {
        this.url = Objects.requireNonNull(url, "Connection URL cannot be null");
    }

    public static boolean accepts(final String url) {
        return url.startsWith("jdbc:tc:");
    }

    /**
     * This method applies various REGEX Patterns to parse the URL associated with this instance.
     * This is called from a @{@link ConnectionUrl#newInstance(String)} static factory method to create immutable instance of
     * {@link ConnectionUrl}.
     * To avoid mutation after class is instantiated, this method should not be publicly accessible.
     */
    private void parseUrl() {
        /*
        Extract from the JDBC connection URL:
         * The database type (e.g. mysql, postgresql, ...)
         * The docker tag, if provided.
         * The URL query string, if provided
       */
        Matcher urlMatcher = Patterns.URL_MATCHING_PATTERN.matcher(this.getUrl());
        if (!urlMatcher.matches()) {
            //Try for Oracle pattern
            urlMatcher = Patterns.ORACLE_URL_MATCHING_PATTERN.matcher(this.getUrl());
            if (!urlMatcher.matches()) {
                throw new IllegalArgumentException(
                    "JDBC URL matches jdbc:tc: prefix but the database or tag name could not be identified"
                );
            }
        }
        databaseType = urlMatcher.group("databaseType");

        imageTag = Optional.ofNullable(urlMatcher.group("imageTag"));

        //String like hostname:port/database name, which may vary based on target database.
        //Clients can further parse it as needed.
        dbHostString = urlMatcher.group("dbHostString");

        //In case it matches to the default pattern
        Matcher dbInstanceMatcher = Patterns.DB_INSTANCE_MATCHING_PATTERN.matcher(dbHostString);
        if (dbInstanceMatcher.matches()) {
            databaseHost = Optional.ofNullable(dbInstanceMatcher.group("databaseHost"));
            databasePort = Optional.ofNullable(dbInstanceMatcher.group("databasePort")).map(Integer::valueOf);
            databaseName = Optional.of(dbInstanceMatcher.group("databaseName"));
        }

        queryParameters =
            Collections.unmodifiableMap(
                parseQueryParameters(Optional.ofNullable(urlMatcher.group("queryParameters")).orElse(""))
            );

        String query = queryParameters
            .entrySet()
            .stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));

        if (query.trim().length() == 0) {
            queryString = Optional.empty();
        } else {
            queryString = Optional.of("?" + query);
        }

        containerParameters = Collections.unmodifiableMap(parseContainerParameters());

        tmpfsOptions = parseTmpfsOptions(containerParameters);

        initScriptPath = Optional.ofNullable(containerParameters.get("TC_INITSCRIPT"));

        reusable = Boolean.parseBoolean(containerParameters.get("TC_REUSABLE"));

        Matcher funcMatcher = Patterns.INITFUNCTION_MATCHING_PATTERN.matcher(this.getUrl());
        if (funcMatcher.matches()) {
            initFunction = Optional.of(new InitFunctionDef(funcMatcher.group(2), funcMatcher.group(4)));
        }

        Matcher daemonMatcher = Patterns.DAEMON_MATCHING_PATTERN.matcher(this.getUrl());
        inDaemonMode = daemonMatcher.matches() && Boolean.parseBoolean(daemonMatcher.group(2));
    }

    private Map<String, String> parseTmpfsOptions(Map<String, String> containerParameters) {
        if (!containerParameters.containsKey("TC_TMPFS")) {
            return Collections.emptyMap();
        }

        String tmpfsOptions = containerParameters.get("TC_TMPFS");

        return Stream
            .of(tmpfsOptions.split(","))
            .collect(Collectors.toMap(string -> string.split(":")[0], string -> string.split(":")[1]));
    }

    /**
     * Get the Testcontainers Parameters such as Init Function, Init Script path etc.
     *
     * @return {@link Map}
     */
    private Map<String, String> parseContainerParameters() {
        Map<String, String> results = new HashMap<>();

        Matcher matcher = Patterns.TC_PARAM_MATCHING_PATTERN.matcher(this.getUrl());
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            results.put(key, value);
        }

        return results;
    }

    /**
     * Get all Query parameters specified in the Connection URL after ?. This DOES NOT include Testcontainers (TC_*) parameters.
     *
     * @return {@link Map}
     */
    private Map<String, String> parseQueryParameters(final String queryString) {
        Map<String, String> results = new HashMap<>();
        Matcher matcher = Patterns.QUERY_PARAM_MATCHING_PATTERN.matcher(queryString);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            if (!key.matches(Patterns.TC_PARAM_NAME_PATTERN)) {
                results.put(key, value);
            }
        }

        return results;
    }

    public Map<String, String> getTmpfsOptions() {
        return Collections.unmodifiableMap(tmpfsOptions);
    }

    /**
     * This interface defines the Regex Patterns used by {@link ConnectionUrl}.
     */
    public interface Patterns {
        Pattern URL_MATCHING_PATTERN = Pattern.compile(
            "jdbc:tc:" +
            "(?<databaseType>[a-z0-9]+)" +
            "(:(?<imageTag>[^:]+))?" +
            "://" +
            "(?<dbHostString>[^?]+)" +
            "(?<queryParameters>\\?.*)?"
        );

        Pattern ORACLE_URL_MATCHING_PATTERN = Pattern.compile(
            "jdbc:tc:" +
            "(?<databaseType>[a-z]+)" +
            "(:(?<imageTag>(?!thin).+))?:thin:(//)?" +
            "(" +
            "(?<username>[^:" +
            "?^/]+)/(?<password>[^?^/]+)" +
            ")?" +
            "@" +
            "(?<dbHostString>[^?]+)" +
            "(?<queryParameters>\\?.*)?"
        );

        //Matches to part of string - hostname:port/databasename
        Pattern DB_INSTANCE_MATCHING_PATTERN = Pattern.compile(
            "((?<databaseHost>[^:]+)" +
            "(:(?<databasePort>[0-9]+))?" +
            "(" +
            "(?<sidOrServiceName>[:/])" +
            "|" +
            ";databaseName=" +
            ")|/)" +
            "(?<databaseName>[^\\\\?]+)"
        );

        Pattern DAEMON_MATCHING_PATTERN = Pattern.compile(".*([?&]?)TC_DAEMON=([^?&]+).*");

        /**
         * @deprecated for removal
         */
        @Deprecated
        Pattern INITSCRIPT_MATCHING_PATTERN = Pattern.compile(".*([?&]?)TC_INITSCRIPT=([^?&]+).*");

        Pattern INITFUNCTION_MATCHING_PATTERN = Pattern.compile(
            ".*([?&]?)TC_INITFUNCTION=" +
            "((\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)*\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)" +
            "::" +
            "(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)" +
            ".*"
        );

        String TC_PARAM_NAME_PATTERN = "(TC_[A-Z_]+)";

        Pattern TC_PARAM_MATCHING_PATTERN = Pattern.compile(TC_PARAM_NAME_PATTERN + "=([^?&]+)");

        Pattern QUERY_PARAM_MATCHING_PATTERN = Pattern.compile("([^?&=]+)=([^?&]*)");
    }

    @Getter
    @AllArgsConstructor
    public class InitFunctionDef {

        private String className;

        private String methodName;
    }
}
