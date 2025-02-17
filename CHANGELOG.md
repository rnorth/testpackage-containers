# Change Log
~All notable changes to this project will be documented in this file.~

# MOVED

**After version 1.8.3 all future releases will _only_ be documented in the [Releases](https://github.com/testcontainers/testcontainers-java/releases) section of the GitHub repository. This changelog file will eventually be removed.**


## [1.8.3] - 2018-08-05

### Fixed

- Fixed `with*` methods of `CouchbaseContainer` ([\#810](https://github.com/testcontainers/testcontainers-java/pull/810))
- Fix problem with gzip encoded streams (e.g. copy file from container), by adding decompression support to netty exec factory (#817, fixes #681, relates to docker-java/docker-java#1079)

## [1.8.2] - 2018-07-31

### Fixed

- Add support for transparently using local images with docker-compose ([\#798](https://github.com/testcontainers/testcontainers-java/pull/798), fixes [\#674](https://github.com/testcontainers/testcontainers-java/issues/674))
- Fix bug with Dockerfile image creation with Docker for Mac 18.06-ce ([\#808](https://github.com/testcontainers/testcontainers-java/pull/808), fixes [\#680](https://github.com/testcontainers/testcontainers-java/issues/680))

### Changed

- Update Visible Assertions to 2.1.1 ([\#779](https://github.com/testcontainers/testcontainers-java/pull/779)).
- KafkaContainer optimization (`group.initial.rebalance.delay.ms=0`) ([\#782](https://github.com/testcontainers/testcontainers-java/pull/782)).

## [1.8.1] - 2018-07-10

### Fixed
- Linux/Mac: Added support for docker credential helpers so that images may be pulled from private registries. See [\#729](https://github.com/testcontainers/testcontainers-java/issues/729), [\#647](https://github.com/testcontainers/testcontainers-java/issues/647) and [\#567](https://github.com/testcontainers/testcontainers-java/issues/567).
- Ensure that the `COMPOSE_FILE` environment variable is populated with all relevant compose file names when running docker-compose in local mode [\#755](https://github.com/testcontainers/testcontainers-java/issues/755).
- Fixed issue whereby specified command in MariaDB image was not being applied. ([\#534](https://github.com/testcontainers/testcontainers-java/issues/534))
- Changed Oracle thin URL to support both Oracle 11 and 12 XE ([\#769](https://github.com/testcontainers/testcontainers-java/issues/769))
- Ensure that full JDBC URL query string is passed to JdbcDatabaseDelegate during initscript invocation ([\#741](https://github.com/testcontainers/testcontainers-java/issues/741); fixes [\#727](https://github.com/testcontainers/testcontainers-java/issues/727))
- Ensure that necessary transitive dependency inclusions are applied to generated project POMs ([\#772](https://github.com/testcontainers/testcontainers-java/issues/772); fixes [\#753](https://github.com/testcontainers/testcontainers-java/issues/753) and [\#652](https://github.com/testcontainers/testcontainers-java/issues/652))

### Changed
- Update Apache Pulsar module to 2.0.1 [\#760](https://github.com/testcontainers/testcontainers-java/issues/760).
- Make JdbcDatabaseContainer#getDriverClassName public [\#743](https://github.com/testcontainers/testcontainers-java/pull/743).
- enable `copyFileToContainer` feature during container startup [\#742](https://github.com/testcontainers/testcontainers-java/pull/742).
- avoid using file mounting in KafkaContainer [\#775](https://github.com/testcontainers/testcontainers-java/pull/775).
- Added Apache Cassandra module [\#776](https://github.com/testcontainers/testcontainers-java/pull/776).

## [1.8.0] - 2018-06-14

### Fixed
- Fixed JDBC URL Regex Pattern to ensure all supported Database URL's are accepted ([\#596](https://github.com/testcontainers/testcontainers-java/issues/596))
- Filtered out TestContainer parameters (TC_*) from query string before passing to database ([\#345](https://github.com/testcontainers/testcontainers-java/issues/345))
- Use `latest` tag as default image tag ([\#676](https://github.com/testcontainers/testcontainers-java/issues/676))

### Changed
- Allow `HttpWaitStrategy` to wait for a specific port ([\#703](https://github.com/testcontainers/testcontainers-java/pull/703))
- New module: Apache Pulsar ([\#713](https://github.com/testcontainers/testcontainers-java/pull/713))
- Add support for defining container labels ([\#725](https://github.com/testcontainers/testcontainers-java/pull/725))
- Use `quay.io/testcontainers/ryuk` instead of `bsideup/ryuk` ([\#721](https://github.com/testcontainers/testcontainers-java/pull/721))
- Added Couchbase module ([\#688](https://github.com/testcontainers/testcontainers-java/pull/688))
- Enhancements and Fixes for JDBC URL usage to create Containers ([\#594](https://github.com/testcontainers/testcontainers-java/pull/594))
    - Extracted JDBC URL manipulations to a separate class - `ConnectionUrl`. 
    - Added an overloaded method `JdbcDatabaseContainerProvider.newInstance(ConnectionUrl)`, with default implementation delegating to the existing `newInstance(tag)` method. (Relates to [\#566](https://github.com/testcontainers/testcontainers-java/issues/566))
    - Added an implementation of `MySQLContainerProvider.newInstance(ConnectionUrl)` that uses Database Name, User, and Password from JDBC URL while creating new MySQL Container. ([\#566](https://github.com/testcontainers/testcontainers-java/issues/566) for MySQL Container)
- Changed **internal** port of KafkaContainer back to 9092 ([\#733](https://github.com/testcontainers/testcontainers-java/pull/733))
- Add support for Dockerfile based images to OracleContainer ([\#734](https://github.com/testcontainers/testcontainers-java/pull/734))
- Read from both `/proc/net/tcp` and `/proc/net/tcp6` in `InternalCommandPortListeningCheck` ([\#750](https://github.com/testcontainers/testcontainers-java/pull/750))
- Added builder methods for timeouts in `JdbcDatabaseContainer` ([\#748](https://github.com/testcontainers/testcontainers-java/pull/748))
- Added an alternative experimental transport based on OkHttp. Enable it with `transport.type=okhttp` property ([\#710](https://github.com/testcontainers/testcontainers-java/pull/710))
- Framework-agnostic container & test lifecycle ([\#702](https://github.com/testcontainers/testcontainers-java/pull/702))

## [1.7.3] - 2018-05-16

### Fixed
- Fix for setting `ryuk.container.timeout` causes a `ClassCastException` ([\#684](https://github.com/testcontainers/testcontainers-java/issues/684))
- Fixed provided but shaded dependencies in modules ([\#693](https://github.com/testcontainers/testcontainers-java/issues/693))

### Changed
- Added InfluxDB module ([\#686](https://github.com/testcontainers/testcontainers-java/pull/686))
- Added MockServer module ([\#696](https://github.com/testcontainers/testcontainers-java/pull/696))
- Changed LocalStackContainer to extend GenericContainer ([\#695](https://github.com/testcontainers/testcontainers-java/pull/695))

## [1.7.2] - 2018-04-30

- Add support for private repositories using docker credential stores/helpers (fixes [\#567](https://github.com/testcontainers/testcontainers-java/issues/567))

### Fixed
- Add support for private repositories using docker credential stores/helpers (fixes [\#567](https://github.com/testcontainers/testcontainers-java/issues/567))
- Retry any exceptions (not just `DockerClientException`) on image pull ([\#662](https://github.com/testcontainers/testcontainers-java/issues/662))
- Fixed handling of the paths with `+` in them ([\#664](https://github.com/testcontainers/testcontainers-java/issues/664))

### Changed
- Database container images are now pinned to a specific version rather than using `latest`. The tags selected are the most recent as of the time of this change. If a JDBC URL is used with no tag specified, a WARN level log message is output, pending a future change to make tags mandatory in the JDBC URL. ([\#671](https://github.com/testcontainers/testcontainers-java/issues/671))
- Updated docker-java to 3.1.0-rc-3, enforced `org.jetbrains:annotations:15.0`. ([\#672](https://github.com/testcontainers/testcontainers-java/issues/672))

## [1.7.1] - 2018-04-20

### Fixed
- Fixed missing `commons-codec` dependency ([\#642](https://github.com/testcontainers/testcontainers-java/issues/642))
- Fixed `HostPortWaitStrategy` throws `NumberFormatException` when port is exposed but not mapped ([\#640](https://github.com/testcontainers/testcontainers-java/issues/640))
- Fixed log processing: multibyte unicode, linebreaks and ASCII color codes. Color codes can be turned on with `withRemoveAnsiCodes(false)` ([\#643](https://github.com/testcontainers/testcontainers-java/pull/643))
- Fixed Docker host IP detection within docker container (detect only if not explicitly set) ([\#648](https://github.com/testcontainers/testcontainers-java/pull/648))
- Add support for private repositories using docker credential stores/helpers ([PR \#647](https://github.com/testcontainers/testcontainers-java/pull/647), fixes [\#567](https://github.com/testcontainers/testcontainers-java/issues/567))

### Changed
- Support multiple HTTP status codes for HttpWaitStrategy ([\#630](https://github.com/testcontainers/testcontainers-java/issues/630))
- Mark all long-living threads started by Testcontainers as daemons and group them. ([\#646](https://github.com/testcontainers/testcontainers-java/issues/646))
- Remove noisy `DEBUG` logging of Netty packets ([\#646](https://github.com/testcontainers/testcontainers-java/issues/646))
- Updated docker-java to 3.1.0-rc-2 ([\#646](https://github.com/testcontainers/testcontainers-java/issues/646))

## [1.7.0] - 2018-04-07

### Fixed
- Fixed extraneous insertion of `useSSL=false` in all JDBC URL strings, even for DBs that do not understand it. Usage is now restricted to MySQL by default and can be overridden by authors of `JdbcDatabaseContainer` subclasses ([\#568](https://github.com/testcontainers/testcontainers-java/issues/568))
- Fixed `getServicePort` on `DockerComposeContainer` throws NullPointerException if service instance number in not used. ([\#619](https://github.com/testcontainers/testcontainers-java/issues/619))
- Increase Ryuk's timeout and make it configurable with `ryuk.container.timeout`. ([\#621](https://github.com/testcontainers/testcontainers-java/issues/621)[\#635](https://github.com/testcontainers/testcontainers-java/issues/635))

### Changed
- Added compatibility with selenium greater than 3.X ([\#611](https://github.com/testcontainers/testcontainers-java/issues/611))
- Abstracted and changed database init script functionality to support use of SQL-like scripts with non-JDBC connections. ([\#551](https://github.com/testcontainers/testcontainers-java/pull/551))
- Added `JdbcDatabaseContainer(Future)` constructor. ([\#543](https://github.com/testcontainers/testcontainers-java/issues/543))
- Mark DockerMachineClientProviderStrategy as not persistable ([\#593](https://github.com/testcontainers/testcontainers-java/pull/593))
- Added `waitingFor(String serviceName, WaitStrategy waitStrategy)` and overloaded `withExposedService()` methods to `DockerComposeContainer` to allow user to define `WaitStrategy` for compose containers. ([\#174](https://github.com/testcontainers/testcontainers-java/issues/174), [\#515](https://github.com/testcontainers/testcontainers-java/issues/515) and ([\#600](https://github.com/testcontainers/testcontainers-java/pull/600)))
- Deprecated `WaitStrategy` and implementations in favour of classes with same names in `org.testcontainers.containers.strategy` ([\#600](https://github.com/testcontainers/testcontainers-java/pull/600))
- Added `ContainerState` interface representing the state of a started container ([\#600](https://github.com/testcontainers/testcontainers-java/pull/600))
- Added `WaitStrategyTarget` interface which is the target of the new `WaitStrategy` ([\#600](https://github.com/testcontainers/testcontainers-java/pull/600))
- *Breaking:* Removed hard-coded `wnameless` Oracle database image name. Users should instead place a file on the classpath named `testcontainers.properties` containing `oracle.container.image=IMAGE`, where IMAGE is a suitable image name and tag/SHA hash. For information, the approach recommended by Oracle for creating an Oracle XE docker image is described [here](https://blogs.oracle.com/oraclewebcentersuite/implement-oracle-database-xe-as-docker-containers).
- Added `DockerHealthcheckWaitStrategy` that is based on Docker's built-in [healthcheck](https://docs.docker.com/engine/reference/builder/#healthcheck) ([\#618](https://github.com/testcontainers/testcontainers-java/pull/618)).
- Added `withLogConsumer(String serviceName, Consumer<OutputFrame> consumer)` method to `DockerComposeContainer` ([\#605](https://github.com/testcontainers/testcontainers-java/issues/605))
- Added `withFixedExposedPort(int hostPort, int containerPort, InternetProtocol protocol)` method to `FixedHostPortGenericContainer` and `addFixedExposedPort(int hostPort, int containerPort, InternetProtocol protocol)` to `GenericContainer` ([\#586](https://github.com/testcontainers/testcontainers-java/pull/586))

## [1.6.0] - 2018-01-28

### Fixed
- Fixed incompatibility of Docker-Compose container with JDK9. ([\#562](https://github.com/testcontainers/testcontainers-java/pull/562))
- Fixed retrieval of Docker host IP when running inside Docker. ([\#479](https://github.com/testcontainers/testcontainers-java/issues/479))
- Compose is now able to pull images from private repositories. ([\#536](https://github.com/testcontainers/testcontainers-java/issues/536))
- Fixed overriding MySQL image command. ([\#534](https://github.com/testcontainers/testcontainers-java/issues/534))
- Fixed shading for javax.annotation.CheckForNull ([\#563](https://github.com/testcontainers/testcontainers-java/issues/563) and [testcontainers/testcontainers-scala\#11](https://github.com/testcontainers/testcontainers-scala/issues/11)).

### Changed
- Added JDK9 build and tests to Travis-CI. ([\#562](https://github.com/testcontainers/testcontainers-java/pull/562))
- Added Kafka module ([\#546](https://github.com/testcontainers/testcontainers-java/pull/546))
- Added "Death Note" to track & kill spawned containers even if the JVM was "kill -9"ed ([\#545](https://github.com/testcontainers/testcontainers-java/pull/545))
- Environment variables are now stored as Map instead of List ([\#550](https://github.com/testcontainers/testcontainers-java/pull/550))
- Added `withEnv(String name, Function<Optional<String>, String> mapper)` with optional previous value ([\#550](https://github.com/testcontainers/testcontainers-java/pull/550))
- Added `withFileSystemBind` overloaded method with `READ_WRITE` file mode by default ([\#550](https://github.com/testcontainers/testcontainers-java/pull/550))
- All connections to JDBC containers (e.g. MySQL) don't use SSL anymore. ([\#374](https://github.com/testcontainers/testcontainers-java/issues/374))

## [1.5.1] - 2017-12-19

### Fixed
- Fixed problem with case-sensitivity when checking internal port. ([\#524](https://github.com/testcontainers/testcontainers-java/pull/524))
- Add retry logic around checkExposedPort pre-flight check for improved robustness ([\#513](https://github.com/testcontainers/testcontainers-java/issues/513))

### Changed
- Added `getDatabaseName` method to JdbcDatabaseContainer, MySQLContainer, PostgreSQLContainer ([\#473](https://github.com/testcontainers/testcontainers-java/issues/473))
- Added `VncRecordingContainer` - Network-based, attachable re-implementation of `VncRecordingSidekickContainer` ([\#526](https://github.com/testcontainers/testcontainers-java/pull/526))

## [1.5.0] - 2017-12-12
### Fixed
- Fixed problems with using container based docker-compose on Windows ([\#514](https://github.com/testcontainers/testcontainers-java/pull/514))
- Fixed problems with copying files on Windows ([\#514](https://github.com/testcontainers/testcontainers-java/pull/514))
- Fixed regression in 1.4.3 when using Docker Compose on Windows ([\#439](https://github.com/testcontainers/testcontainers-java/issues/439))
- Fixed local Docker Compose executable name resolution on Windows ([\#416](https://github.com/testcontainers/testcontainers-java/issues/416))
- Fixed TAR composition on Windows ([\#444](https://github.com/testcontainers/testcontainers-java/issues/444))
- Allowing `addExposedPort` to be used after ports have been specified with `withExposedPorts` ([\#453](https://github.com/testcontainers/testcontainers-java/issues/453))
- Stopping creation of temporary directory prior to creating temporary file ([\#443](https://github.com/testcontainers/testcontainers-java/issues/443))
- Ensure that temp files are created in a temp directory ([\#423](https://github.com/testcontainers/testcontainers-java/issues/423))
- Added `WaitAllStrategy` as a mechanism for composing multiple startup `WaitStrategy` objects together
- Changed `BrowserWebDriverContainer` to use improved wait strategies, to eliminate race conditions when starting VNC recording containers. This should lead to far fewer 'error' messages logged when starting up selenium containers, and less exposure to race related bugs (fixes [\#466](https://github.com/testcontainers/testcontainers-java/issues/466)).

### Changed
- Make Network instances reusable (i.e. work with `@ClassRule`) ([\#469](https://github.com/testcontainers/testcontainers-java/issues/469))
- Added support for explicitly setting file mode when copying file into container ([\#446](https://github.com/testcontainers/testcontainers-java/issues/446), [\#467](https://github.com/testcontainers/testcontainers-java/issues/467))
- Use Visible Assertions 2.1.0 for pre-flight test output (eliminating Jansi/JNR-POSIX dependencies for lower likelihood of conflict. JNA is now used internally by Visible Assertions instead).
- Mark all links functionality as deprecated. This is pending removal in a later release. Please see [\#465](https://github.com/testcontainers/testcontainers-java/issues/465). `Network` features should be used instead.
- Added support for copying files to/from running containers ([\#378](https://github.com/testcontainers/testcontainers-java/issues/378))
- Add `getLivenessCheckPorts` as an eventual replacement for `getLivenessCheckPort`; this allows multiple ports to be included in post-startup wait strategies.
- Refactor wait strategy port checking and improve test coverage.
- Added support for customising the recording file name ([\#500](https://github.com/testcontainers/testcontainers-java/issues/500))

## [1.4.3] - 2017-10-14
### Fixed
- Fixed local Docker Compose executable name resolution on Windows ([\#416](https://github.com/testcontainers/testcontainers-java/issues/416), [\#460](https://github.com/testcontainers/testcontainers-java/issues/460))
- Fixed TAR composition on Windows ([\#444](https://github.com/testcontainers/testcontainers-java/issues/444))
- Allowing `addExposedPort` to be used after ports have been specified with `withExposedPorts` ([\#453](https://github.com/testcontainers/testcontainers-java/issues/453))
- Stopping creation of temporary directory prior to creating temporary file ([\#443](https://github.com/testcontainers/testcontainers-java/issues/443))

### Changed
- Added `forResponsePredicate` method to HttpWaitStrategy to test response body ([\#441](https://github.com/testcontainers/testcontainers-java/issues/441))
- Changed `DockerClientProviderStrategy` to be loaded via Service Loader ([\#434](https://github.com/testcontainers/testcontainers-java/issues/434), [\#435](https://github.com/testcontainers/testcontainers-java/issues/435))
- Made it possible to specify docker compose container in configuration ([\#422](https://github.com/testcontainers/testcontainers-java/issues/422), [\#425](https://github.com/testcontainers/testcontainers-java/issues/425))
- Clarified wording of pre-flight check messages ([\#457](https://github.com/testcontainers/testcontainers-java/issues/457), [\#436](https://github.com/testcontainers/testcontainers-java/issues/436))
- Added caching of failure to find a docker daemon, so that subsequent tests fail fast. This is likely to be a significant improvement in situations where there is no docker daemon available, dramatically reducing run time and log output when further attempts to find the docker daemon cannot succeed.
- Allowing JDBC containers' username, password and DB name to be customized ([\#400](https://github.com/testcontainers/testcontainers-java/issues/400), [\#354](https://github.com/testcontainers/testcontainers-java/issues/354))

## [1.4.2] - 2017-07-25
### Fixed
- Worked around incompatibility between Netty's Unix socket support and OS X 10.11. Reinstated use of TCP-Unix Socket proxy when running on OS X prior to v10.12. (Fixes [\#402](https://github.com/testcontainers/testcontainers-java/issues/402))
- Changed to use version 2.0 of the Visible Assertions library for startup pre-flight checks. This no longer has a dependency on Jansi, and is intended to resolve a JVM crash issue apparently caused by native lib version conflicts ([\#395](https://github.com/testcontainers/testcontainers-java/issues/395)). Please note that the newer ANSI code is less mature and thus has had less testing, particularly in interesting terminal environments such as Windows. If issues are encountered, coloured assertion output may be disabled by setting the system property `visibleassertions.ansi.enabled` to `true`.
- Fixed NullPointerException when calling GenericContainer#isRunning on not started container ([\#411](https://github.com/testcontainers/testcontainers-java/issues/411))

### Changed
- Removed Guava usage from `jdbc` module ([\#401](https://github.com/testcontainers/testcontainers-java/issues/401))

## [1.4.1] - 2017-07-10
### Fixed
- Fixed Guava shading in `jdbc` module

## [1.4.0] - 2017-07-09
### Fixed
- Fixed the case when disk's size is bigger than Integer's max value ([\#379](https://github.com/testcontainers/testcontainers-java/issues/379), [\#380](https://github.com/testcontainers/testcontainers-java/issues/380))
- Fixed erroneous version reference used during CI testing of shaded dependencies
- Fixed leakage of Vibur and Tomcat JDBC test dependencies in `jdbc-test` and `mysql` modules ([\#382](https://github.com/testcontainers/testcontainers-java/issues/382))
- Added timeout and retries for creation of `RemoteWebDriver` ([\#381](https://github.com/testcontainers/testcontainers-java/issues/381), [\#373](https://github.com/testcontainers/testcontainers-java/issues/373), [\#257](https://github.com/testcontainers/testcontainers-java/issues/257))
- Fixed various shading issues
- Improved removal of containers/networks when using Docker Compose, eliminating irrelevant errors during cleanup ([\#342](https://github.com/testcontainers/testcontainers-java/issues/342), [\#394](https://github.com/testcontainers/testcontainers-java/issues/394))

### Changed
- Added support for Docker networks ([\#372](https://github.com/testcontainers/testcontainers-java/issues/372))
- Added `getFirstMappedPort` method ([\#377](https://github.com/testcontainers/testcontainers-java/issues/377))
- Extracted Oracle XE container into a separate repository ([testcontainers/testcontainers-java-module-oracle-xe](https://github.com/testcontainers/testcontainers-java-module-oracle-xe))
- Added shading tests
- Updated docker-java to 3.0.12 ([\#393](https://github.com/testcontainers/testcontainers-java/issues/393))

## [1.3.1] - 2017-06-22
### Fixed
- Fixed non-POSIX fallback for file attribute reading ([\#371](https://github.com/testcontainers/testcontainers-java/issues/371))
- Fixed NullPointerException in AuditLogger when running using slf4j-log4j12 bridge ([\#375](https://github.com/testcontainers/testcontainers-java/issues/375))
- Improved cleanup of JDBC connections during database container startup checks

### Changed
- Extracted MariaDB into a separate repository ([\#337](https://github.com/testcontainers/testcontainers-java/issues/337))
- Added `TC_DAEMON` JDBC URL flag to prevent `ContainerDatabaseDriver` from shutting down containers at the time all connections are closed. ([\#359](https://github.com/testcontainers/testcontainers-java/issues/359), [\#360](https://github.com/testcontainers/testcontainers-java/issues/360))
- Added pre-flight checks (can be disabled with `checks.disable` configuration property) ([\#363](https://github.com/testcontainers/testcontainers-java/issues/363))
- Improved startup time by adding dynamic priorities to DockerClientProviderStrategy ([\#362](https://github.com/testcontainers/testcontainers-java/issues/362))
- Added global configuration file `~/.testcontainers.properties` ([\#362](https://github.com/testcontainers/testcontainers-java/issues/362))
- Added container arguments to specify SELinux contexts for mounts ([\#334](https://github.com/testcontainers/testcontainers-java/issues/334))
- Removed unused Jersey dependencies ([\#361](https://github.com/testcontainers/testcontainers-java/issues/361))
- Removed deprecated, wrongly-generated setters from `GenericContainer`

## [1.3.0] - 2017-06-05
### Fixed
- Improved container cleanup if startup failed ([\#336](https://github.com/testcontainers/testcontainers-java/issues/336), [\#335](https://github.com/testcontainers/testcontainers-java/issues/335))

### Changed
- Upgraded docker-java library to 3.0.10 ([\#349](https://github.com/testcontainers/testcontainers-java/issues/349))
- Added basic audit logging of Testcontainers' actions via a specific SLF4J logger name with metadata captured via MDC. Intended for use in highly shared Docker environments.
- Use string-based detection of Selenium container startup ([\#328](https://github.com/testcontainers/testcontainers-java/issues/328), [\#351](https://github.com/testcontainers/testcontainers-java/issues/351))
- Use string-based detection of PostgreSQL container startup ([\#327](https://github.com/testcontainers/testcontainers-java/issues/327), [\#317](https://github.com/testcontainers/testcontainers-java/issues/317))
- Update libraries to recent versions ([\#333](https://github.com/testcontainers/testcontainers-java/issues/333))
- Introduce abstraction over files and classpath resources, allowing recursive copying of directories ([\#313](https://github.com/testcontainers/testcontainers-java/issues/313))

## [1.2.1] - 2017-04-06
### Fixed
- Fix bug in space detection when `alpine:3.5` image has not yet been pulled ([\#323](https://github.com/testcontainers/testcontainers-java/issues/323), [\#324](https://github.com/testcontainers/testcontainers-java/issues/324))
- Minor documentation fixes

### Changed
- Add AOP Alliance dependencies to shaded deps to reduce chance of conflicts ([\#315](https://github.com/testcontainers/testcontainers-java/issues/315))

## [1.2.0] - 2017-03-12
### Fixed
- Fix various escaping issues that may arise when paths contain spaces ([\#263](https://github.com/testcontainers/testcontainers-java/issues/263), [\#279](https://github.com/testcontainers/testcontainers-java/issues/279))
- General documentation fixes/improvements ([\#300](https://github.com/testcontainers/testcontainers-java/issues/300), [\#303](https://github.com/testcontainers/testcontainers-java/issues/303), [\#304](https://github.com/testcontainers/testcontainers-java/issues/304))
- Improve reliability of `ResourceReaper` when there are a large number of containers returned by `docker ps -a` ([\#295](https://github.com/testcontainers/testcontainers-java/issues/295))

### Changed
- Support Docker for Windows via TCP socket connection ([\#291](https://github.com/testcontainers/testcontainers-java/issues/291), [\#297](https://github.com/testcontainers/testcontainers-java/issues/297), [\#309](https://github.com/testcontainers/testcontainers-java/issues/309)). _Note that Docker Compose is not yet supported under Docker for Windows (see [\#306](https://github.com/testcontainers/testcontainers-java/issues/306))
- Expose `docker-java`'s `CreateContainerCmd` API for low-level container tweaking ([\#301](https://github.com/testcontainers/testcontainers-java/issues/301))
- Shade `org.newsclub` and Guava dependencies ([\#299](https://github.com/testcontainers/testcontainers-java/issues/299), [\#292](https://github.com/testcontainers/testcontainers-java/issues/292))
- Add `org.testcontainers` label to all containers created by Testcontainers ([\#294](https://github.com/testcontainers/testcontainers-java/issues/294))

## [1.1.9] - 2017-02-12
### Fixed
- Fix inability to run Testcontainers on Alpine linux. Unix-socket-over-TCP is now used in linux environments where netty fails due to lack of glibc libraries ([\#290](https://github.com/testcontainers/testcontainers-java/issues/290))
- Fix slow feedback in the case of missing JDBC drivers by failing-fast if the required driver cannot be found ([\#280](https://github.com/testcontainers/testcontainers-java/issues/280), [\#230](https://github.com/testcontainers/testcontainers-java/issues/230))

### Changed
- Add ability to change 'tiny image' used for disk space checks ([\#287](https://github.com/testcontainers/testcontainers-java/issues/287))
- Add ability to attach volumes to a container using 'volumes from' ([\#244](https://github.com/testcontainers/testcontainers-java/issues/244), [\#289](https://github.com/testcontainers/testcontainers-java/issues/289))

## [1.1.8] - 2017-01-22
### Fixed
- Compatibility fixes for Docker for Mac v1.13.0 ([\#272](https://github.com/testcontainers/testcontainers-java/issues/272))
- Relax docker environment disk space check to accommodate unusual empty `df` output observed on Docker for Mac with OverlayFS ([\#273](https://github.com/testcontainers/testcontainers-java/issues/273), [\#278](https://github.com/testcontainers/testcontainers-java/issues/278))
- Fix inadvertent private-scoping of startup checks' `StartupStatus`, which made implementation of custom startup checks impossible ([\#266](https://github.com/testcontainers/testcontainers-java/issues/266))
- Fix potential resource lead/deadlock when errors are encountered building images from a Dockerfile ([\#274](https://github.com/testcontainers/testcontainers-java/issues/274))

### Changed
- Add support for execution within a Docker container ([\#267](https://github.com/testcontainers/testcontainers-java/issues/267)), correcting resolution of container addresses
- Add support for version 2 of private docker registries, configured via `$HOME/.docker/config.json` ([\#270](https://github.com/testcontainers/testcontainers-java/issues/270))
- Use current classloader instead of system classloader for loading JDBC drivers ([\#261](https://github.com/testcontainers/testcontainers-java/issues/261))
- Allow hardcoded container image names for Ambassador and VNC recorder containers to be changed via a configuration file ([\#277](https://github.com/testcontainers/testcontainers-java/issues/277), [\#259](https://github.com/testcontainers/testcontainers-java/issues/259))
- Allow Selenium Webdriver container image name to be specified as a constructor parameter ([\#249](https://github.com/testcontainers/testcontainers-java/issues/249), [\#171](https://github.com/testcontainers/testcontainers-java/issues/171))


## [1.1.7] - 2016-11-19
### Fixed
- Compensate for premature TCP socket opening in Docker for Mac ([\#160](https://github.com/testcontainers/testcontainers-java/issues/160), [\#236](https://github.com/testcontainers/testcontainers-java/issues/236))
- (Internal) Stabilise various parts of Testcontainers' self test suite ([\#241](https://github.com/testcontainers/testcontainers-java/issues/241))
- Fix mounting of classpath resources when those resources are in a JAR file ([\#213](https://github.com/testcontainers/testcontainers-java/issues/213))
- Reduce misleading error messages caused mainly by trying to perform operations on stopped containers ([\#243](https://github.com/testcontainers/testcontainers-java/issues/243))

### Changed
- Uses a default MySQL and MariaDB configuration to reduce memory footprint ([\#209](https://github.com/testcontainers/testcontainers-java/issues/209), [\#243](https://github.com/testcontainers/testcontainers-java/issues/243))
- Docker Compose can optionally now use a local `docker-compose` executable rather than running inside a container ([\#200](https://github.com/testcontainers/testcontainers-java/issues/200))
- Add support for privileged mode containers ([\#234](https://github.com/testcontainers/testcontainers-java/issues/234), [\#235](https://github.com/testcontainers/testcontainers-java/issues/235))
- Allow container/network cleanup (ResourceReaper) to be triggered programmatically ([\#231](https://github.com/testcontainers/testcontainers-java/issues/231))
- Add optional tailing of logs for containers spawned by Docker Compose ([\#233](https://github.com/testcontainers/testcontainers-java/issues/233))
- (Internal) Relocate non-proprietary database container tests to a single module

## [1.1.6] - 2016-09-22
### Fixed
- Fix logging of discovered Docker environment variables ([\#218](https://github.com/testcontainers/testcontainers-java/issues/218))
- Adopt longer timeout periods for testing docker client configurations, and allow these to be further customised through system properties ([\#217](https://github.com/testcontainers/testcontainers-java/issues/217), see *ClientProviderStrategy classes)
- Fix docker compose directory mounting on windows ([\#224](https://github.com/testcontainers/testcontainers-java/issues/224))
- Handle and ignore further categories of failure in retrieval of docker environment disk space ([\#225](https://github.com/testcontainers/testcontainers-java/issues/225))

### Changed
- Add extra configurability options (database name, username, password) for PostgreSQL DB containers ([\#220](https://github.com/testcontainers/testcontainers-java/issues/220))
- Add MariaDB container type ([\#215](https://github.com/testcontainers/testcontainers-java/issues/215))
- Use Docker Compose `down` action for more robust teardown of compose environments
- Ensure that Docker Compose operations run sequentially rather than concurrently if JUnit tests are parallelized ([\#226](https://github.com/testcontainers/testcontainers-java/issues/226))
- Allow multiple Docker Compose files to be specified, to allow for extension/composition of services ([\#227](https://github.com/testcontainers/testcontainers-java/issues/227))

## [1.1.5] - 2016-08-22
### Fixed
- Fix Docker Compose environment variable passthrough ([\#208](https://github.com/testcontainers/testcontainers-java/issues/208))

### Changed
- Remove Docker Compose networks when containers are shut down ([\#211](https://github.com/testcontainers/testcontainers-java/issues/211)) as well as at JVM shutdown

## [1.1.4] - 2016-08-16
### Fixed
- Fix JDBC proxy driver behaviour when used with Tomcat connection pool to avoid spawning excessive numbers of containers ([\#195](https://github.com/testcontainers/testcontainers-java/issues/195))
- Shade Jersey dependencies in JDBC module to avoid classpath conflicts ([\#202](https://github.com/testcontainers/testcontainers-java/issues/202))
- Fix NullPointerException when docker host has untagged images ([\#201](https://github.com/testcontainers/testcontainers-java/issues/201))
- Fix relative paths for volumes mounted in docker-compose containers ([\#189](https://github.com/testcontainers/testcontainers-java/issues/189))

### Changed
- Update to v3.0.2 of docker-java library
- Switch to a shared, single instance docker client rather than a separate client instance per container rule ([\#193](https://github.com/testcontainers/testcontainers-java/issues/193))
- Ensure that docker-compose pulls images (with no timeout), prior to trying to start ([\#188](https://github.com/testcontainers/testcontainers-java/issues/188))
- Use official `docker/compose` image for running docker-compose ([\#190](https://github.com/testcontainers/testcontainers-java/issues/190))

## [1.1.3] - 2016-07-27
### Fixed
- Further fix for shading of netty Linux native libs, specifically when run using Docker Compose support
- Ensure that file mode permissions are retained for Dockerfile builder

### Changed
- Add support for specifying container working directory, and set this to match the `/compose` directory for Docker Compose
- Improve resilience of Selenium container startup
- Add `withLogConsumer(...)` to allow a log consumer to be attached to a container from the moment of startup

## [1.1.2] - 2016-07-19
### Fixed
- Fix shading of netty Linux native libs

### Changed
- Shade guava artifacts to prevent classloader conflicts

## [1.1.1] - 2016-07-17
### Fixed
- Improve shutdown of unnecessary docker clients ([\#170](https://github.com/testcontainers/testcontainers-java/issues/170))
- Shade `io.netty` dependencies into the testcontainers core JAR to reduce conflicts ([\#170](https://github.com/testcontainers/testcontainers-java/issues/170) and [\#157](https://github.com/testcontainers/testcontainers-java/issues/157))
- Remove timeouts for docker compose execution, particularly useful when image pulls are involved
- Improve output logging from docker-compose, pausing to log output in case of failure rather than letting logs intermingle.

### Changed
- Reinstate container startup retry (removed in v1.1.0) as an optional setting, only used by default for Selenium webdriver containers

## [1.1.0] - 2016-07-05
### Fixed
- Apply shade relocation to Jersey repackaged Guava libs
- General logging and stability improvements to Docker Compose support
- Fix liveness checks to use specific IP address obtained using `getContainerIpAddress()`

### Changed
- Integrate interim support for Docker for Mac beta and Docker Machine for Windows. See [docs](docs/index.md) for known limitations.
- Add support for Docker Compose v2 and scaling of compose containers
- Add support for attaching containers to specific networks.
- Allow container environment variables to be set using a Map

## [1.0.5] - 2016-05-02
### Fixed
- Fix problems associated with changes to `tenforce/virtuoso:latest` container, and replace with a pinned version.
- Fix build-time dependency on visible-assertions library, which had downstream dependencies that started to break the Testcontainers build.

### Changed
- Add support for pluggable wait strategies, i.e. overriding the default TCP connect wait strategy with HTTP ping or any user-defined approach.
- Add 'self-typing' to allow easy use of fluent-style options even when `GenericContainer` is subclassed.
- Add support for defining extra entries for containers' `/etc/hosts` files.
- Add fluent setter for setting file-system file/directory binding

## [1.0.4] - 2016-04-17
### Fixed
- Prevent unnecessary and erroneous reconfiguration of container if startup needs to be retried
- Consolidate container cleanup to ensure that ambassador containers used for Docker Compose are cleaned up appropriately
- Fix container liveness check port lookup for FixedHostPortGenericContainer.
- Upgrade docker-compose container to dduportal/docker-compose:1.6.0 for compatibility with docker compose file format v2.

### Changed
- Add `docker exec` support for running commands against running containers
- Add support for building container images on the fly from Dockerfiles, including optional Dockerfile builder DSL
- Add container name as prefix for container logs that are streamed to SLF4J
- Improve container startup failure detection, including adding the option to specify a minimum up time that the container should achieve before being considered started successfully

## [1.0.3] - 2016-03-31
### Fixed
- Resolve issues where containers would not be cleaned up on JVM shutdown if they failed to start correctly
- Fix validation problem where docker image names that contained private registry URLs with port number would be rejected
- Resolve bug where `docker pull` would try infinitely for a non-existent image name

### Changed
- Set startup free disk space check to ensure that the Docker environment has a minimum of 2GB available rather than 10%
- Add streaming of container logs to SLF4J loggers, capture as Strings, and also the ability to wait for container log content to satisfy an expected predicate
- Allow configuration of docker container startup timeout
- Add detection of classpath Selenium version, and automatic selection of correct Selenium docker containers for compatibility

## [1.0.2] - 2016-02-27
### Fixed
- If a container fail to start up correctly, startup will now be retried up to a limit of 3 times
- Add resilience around `getMappedPort` method to fail fast when a port is not yet mapped, rather than generate misleading errors

### Changed
- Add JDBC container module for OpenLink Virtuoso
- Add additional debug level logging to aid with diagnosis of docker daemon discovery problems
- Add support for using a local Unix socket to connect to the Docker daemon

## [1.0.1] - 2016-02-18
### Fixed
- Remove extraneous service loader entries in the shaded JAR
- Upgrade to v2.2.0 of docker-java client library to take advantage of unix socket fixes (see https://github.com/docker-java/docker-java/issues/456)
- Validate that docker image names include a tag on creation

### Changed
- By default, use docker machine name from `DOCKER_MACHINE_NAME` environment, or `default` if it exists
- Allow container ports to map to a fixed port on the host through use of the `FixedHostPortGenericContainer` subclass of `GenericContainer`

## [1.0.0] - 2016-02-07
### Fixed
- Resolve Jersey/Jackson dependency clashes by shading (relocating) a version of these libraries into the core Testcontainers JAR
- Improve documentation and logging concerning discovery of Docker daemon

### Changed
- Rename container `getIpAddress()` method to `getContainerIpAddress()` and deprecate original method name.
- Rename container `getHostIpAddress()` method to `getTestHostIpAddress()`

## [0.9.9] - 2016-01-12
### Fixed
- Resolve thread safety issues associated with use of a singleton docker client
- Resolve disk space check problems when running on a Debian-based docker host
- Fix CircleCI problems where the build could hit memory limits

### Changed
- Remove bundled logback.xml to allow users more control over logging
- Add Travis CI support for improved breadth of testing

## [0.9.8] - 2015-08-12
### Changed
- Change from Spotify docker client library to docker-java, for improved compatibility with latest versions of Docker
- Change from JDK 1.7 minimum requirement to JDK 1.8
- Replace boot2docker support with docker-machine support
- Docker images are now prefetched when a @Rule is instantiated
- Combined Rule and Container classes throughout, for a reduced set of public classes and removal of some duplication
- Improvements to container cleanup, especially removal of data volumes
- General improvements to error handling, logging etc throughout

### Added
- Docker Compose support
- Automatic docker environment disk space check

## [0.9.7] - 2015-08-07
### Added
- Support for overriding MySQL container configuration (my.cnf file overrides)

### Changed
- Replace dependency on org.testpackage with org.rnorth.visible-assertions

## [0.9.6] - 2015-07-22
### Added
- Generic container support (allows use of any docker image) using a GenericContainerRule.

### Changed
- Renamed from org.rnorth.test-containers to org.testcontainers
- Explicit support for usage on linux and use with older versions of Docker (v1.2.0 tested)

## [0.9.5] - 2015-06-28
### Added
- Oracle XE container support

### Changed
- Support for JDK 1.7 (previously was JDK 1.8+)

## [0.9.4] and 0.9.3 - 2015-06-23
### Changed
- Refactored for better modularization

## [0.9.2] - 2015-06-13
### Added
- 'Sidekick' VNC recording container to record video of Selenium test sessions

### Changed
- Alter timezone used for time display inside Selenium containers

## [0.9.1] - 2015-06-07
### Added
- Support for Selenium webdriver containers
- Recording of Selenium test sessions using vnc2flv

## [0.9] - 2015-04-29
Initial release

[1.5.1]: https://github.com/testcontainers/testcontainers-java/releases/tag/1.5.1
[1.5.0]: https://github.com/testcontainers/testcontainers-java/releases/tag/1.5.0
[1.4.2]: https://github.com/testcontainers/testcontainers-java/releases/tag/1.4.3
[1.4.2]: https://github.com/testcontainers/testcontainers-java/releases/tag/1.4.2
[1.4.1]: https://github.com/testcontainers/testcontainers-java/releases/tag/1.4.1
[1.4.0]: https://github.com/testcontainers/testcontainers-java/releases/tag/1.4.0
[1.2.0]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.2.0
[1.1.9]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.9
[1.1.8]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.8
[1.1.7]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.7
[1.1.6]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.6
[1.1.5]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.5
[1.1.4]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.4
[1.1.3]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.3
[1.1.2]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.2
[1.1.1]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.1
[1.1.0]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.1.0
[1.0.5]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.0.5
[1.0.4]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.0.4
[1.0.3]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.0.3
[1.0.2]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.0.2
[1.0.1]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.0.1
[1.0.0]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-1.0.0
[0.9.9]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-0.9.9
[0.9.8]: https://github.com/testcontainers/testcontainers-java/releases/tag/testcontainers-0.9.8
[0.9.7]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.7
[0.9.6]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.6
[0.9.5]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.5
[0.9.4]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.4
[0.9.3]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.3
[0.9.2]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.2
[0.9.1]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9.1
[0.9]: https://github.com/testcontainers/testcontainers-java/releases/tag/test-containers-0.9
