description = "TestContainers :: HiveMQ"

plugins {
    `java-library`
}

dependencies {
    api(project(":testcontainers"))
    api("org.jetbrains:annotations:23.0.0")

    shaded("org.apache.commons:commons-lang3:3.12.0")
    shaded("commons-io:commons-io:2.11.0")
    shaded("org.javassist:javassist:3.28.0-GA")
    shaded("org.jboss.shrinkwrap:shrinkwrap-api:1.2.6")
    shaded("org.jboss.shrinkwrap:shrinkwrap-impl-base:1.2.6")
    shaded("net.lingala.zip4j:zip4j:2.9.0")

    /*  This dependency needs to be explicitly added, because shrinkwrap-resolver-api-maven-embedded
        and shrinkwrap-resolver-impl-maven-embedded depend on different versions of it.
        This would lead to issues when the HiveMQ container is included in maven projects. */
    shaded("org.codehaus.plexus:plexus-utils:3.2.1")
    shaded("org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-api-maven-embedded:3.1.4") {
        exclude("org.codehouse.plexus", "plexus-utils")
    }
    runtimeOnly("org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-impl-maven-embedded:3.1.4") {
        exclude("org.codehouse.plexus", "plexus-utils")
    }

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testImplementation("com.hivemq:hivemq-extension-sdk:4.7.3")
    testImplementation("com.hivemq:hivemq-mqtt-client:1.3.0")
    testImplementation("org.apache.httpcomponents:httpclient:4.5.13")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<JavaCompile>("compileTestJava") {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(11))
    })
}
