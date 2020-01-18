package org.testcontainers.utility;

import org.apache.commons.lang.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.rnorth.visibleassertions.VisibleAssertions.assertThat;
import static org.rnorth.visibleassertions.VisibleAssertions.assertTrue;

public class DirectoryTarResourceTest {
    @Test
    public void simpleRecursiveFileTest() {
        // 'src' is expected to be the project base directory, so all source code/resources should be copied in
        File directory = new File("src");

        try (GenericContainer container = new GenericContainer(
            new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                    builder.from("alpine:3.14")
                        .copy("/tmp/foo", "/foo")
                        .cmd("cat /foo/test/resources/test-recursive-file.txt")
                        .build()
                ).withFileFromFile("/tmp/foo", directory))
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy())) {

            container.start();

            final String results = container.getLogs();

            assertTrue("The container has a file that was copied in via a recursive copy", results.contains("Used for DirectoryTarResourceTest"));
        }
    }

    @Test
    public void simpleRecursiveFileWithPermissionTest() {
        try (GenericContainer container = new GenericContainer(
            new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                    builder.from("alpine:3.14")
                        .copy("/tmp/foo", "/foo")
                        .cmd("ls", "-al", "/")
                        .build()
                ).withFileFromFile("/tmp/foo", new File("/mappable-resource/test-resource.txt"),
                0754))
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy())) {

            container.start();
            String listing = container.getLogs();

            assertThat("Listing shows that file is copied with mode requested.",
                Arrays.asList(listing.split("\\n")),
                exactlyOnce(allOf(containsString("-rwxr-xr--"), containsString("foo"))));
        }
    }

    @Test
    public void transferFileDockerDaemon() {
        final File theFile = new File("src/test/resources/mappable-resource/test-resource.txt");
        try (GenericContainer container = new GenericContainer(
            new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                    builder.from("alpine:3.3")
                        .copy(".", "/foo/")
                        .cmd("ls", "-lapR", "/foo")
                        .build()
                )
                .withFileFromFile("bar1", theFile)
                .withFileFromFile("./bar2", theFile)
                .withFileFromFile("../bar3", theFile)
                .withFileFromFile(".bar4", theFile)
                .withFileFromFile("..bar5", theFile)
                .withFileFromFile("xxx/../bar6", theFile)
                .withFileFromFile("x7/./bar7", theFile)
                .withFileFromFile("x8/././bar8", theFile)
                .withFileFromFile("x9/../../bar9", theFile))
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy())) {

            container.start();

            final List<String> logLines = Arrays.asList(container.getLogs().split("\\n"));
            assertThat("Three groups of dirs", logLines.stream()
                .filter(StringUtils::isEmpty)
                .count(), equalTo(2L));

            // parent dir + 2 sub dirs
            List<String> parentDir = null;
            List<String> subDir1 = null;
            int start = 0;
            for (int i = 0; i < logLines.size(); i++) {
                if (isEmpty(logLines.get(i))) {
                    if (parentDir == null) {
                        parentDir = new ArrayList<>(logLines.subList(start, i));
                        start = i;
                    } else if (subDir1 == null) {
                        subDir1 = new ArrayList<>(logLines.subList(start, i));
                        start = i;
                    }
                }
            }
            List<String> subDir2 = new ArrayList<>(logLines.subList(start, logLines.size()));

            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" bar1")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" bar2")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" bar3")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" .bar4")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" ..bar5")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" bar6")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" x7/")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" x8/")));
            assertThat("Listing shows that file is copied.", parentDir, exactlyOnce(endsWith(" bar9")));
            assertThat("Listing shows that file is copied.", subDir1, exactlyOnce(endsWith(" bar7")));
            assertThat("Listing shows that file is copied.", subDir2, exactlyOnce(endsWith(" bar8")));
        }
    }

    @Test
    public void simpleRecursiveClasspathResourceTest() {
        // This test combines the copying of classpath resources from JAR files with the recursive TAR approach, to allow JARed classpath resources to be copied in to an image

        try (GenericContainer container = new GenericContainer(
            new ImageFromDockerfile()
                .withDockerfileFromBuilder(builder ->
                    builder.from("alpine:3.14")
                        .copy("/tmp/foo", "/foo")
                        .cmd("ls -lRt /foo")
                        .build()
                ).withFileFromClasspath("/tmp/foo", "/recursive/dir"))          // here we use /org/junit as a directory that really should exist on the classpath
            .withStartupCheckStrategy(new OneShotStartupCheckStrategy())) {

            container.start();

            final String results = container.getLogs();

            // ExternalResource.class is known to exist in a subdirectory of /org/junit so should be successfully copied in
            assertTrue("The container has a file that was copied in via a recursive copy from a JAR resource", results.contains("content.txt"));
        }
    }

    public static <T> Matcher<Iterable<? super T>> exactlyOnce(Matcher<? super T> elementMatcher) {
        return exactlyNItems(1, elementMatcher);
    }

    public static <T> Matcher<Iterable<? super T>> exactlyNItems(final int n, Matcher<? super T> elementMatcher) {
        return new IsCollectionContaining<T>(elementMatcher) {
            @Override
            protected boolean matchesSafely(Iterable<? super T> collection, Description mismatchDescription) {
                int count = 0;
                boolean isPastFirst = false;

                for (Object item : collection) {

                    if (elementMatcher.matches(item)) {
                        count++;
                    }
                    if (isPastFirst) {
                        mismatchDescription.appendText(", ");
                    }
                    elementMatcher.describeMismatch(item, mismatchDescription);
                    isPastFirst = true;
                }

                if (count != n) {
                    mismatchDescription.appendText(". Expected exactly " + n + " but got " + count);
                }
                return count == n;
            }
        };
    }
}
