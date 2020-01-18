package org.testcontainers.images.builder;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.file.Paths;

@RunWith(Enclosed.class)
public class ImageFromDockerfileTest {
    @RunWith(Parameterized.class)
    public static class InvalidTarPathTests {
        @Rule
        public final ExpectedException expectedException = ExpectedException.none();

        @Parameterized.Parameters(name = "{index} - {0}")
        public static String[] getTestCases() {
            return new String[]{
                "..",
                ".",
                "../",
                "./",
                "xxx/",
                "yyy/xxx/",
                "/xxx/",
                "/yyy/xxx/",
                "/..",
                "/.",
                "/../",
                "/./",
                ".",
                "..",
                "aa/.",
                "aa/..",
                "bb/./",
                "bb/../",
                "cc./",
                "cc../"
            };
        }

        @Parameterized.Parameter
        public String tarPath;

        @Test
        public void unableToTransferFileWithDotsToDockerDaemon() {
            expectedException.expect(IllegalArgumentException.class);
            expectedException.expectMessage("Unable to store file '" +
                Paths.get("src", "test", "resources", "mappable-resource", "test-resource.txt") +
                "' to docker path '" + tarPath + "'");

            new ImageFromDockerfile()
                .withFileFromFile(tarPath, new File("src/test/resources/mappable-resource/test-resource.txt"));
        }
    }

    @Test
    public void shouldAddDefaultLabels() {
        ImageFromDockerfile image = new ImageFromDockerfile()
            .withDockerfileFromBuilder(it -> it.from("scratch"));

        String imageId = image.resolve();

        DockerClient dockerClient = DockerClientFactory.instance().client();

        InspectImageResponse inspectImageResponse = dockerClient.inspectImageCmd(imageId).exec();

        assertThat(inspectImageResponse.getConfig().getLabels())
            .containsAllEntriesOf(DockerClientFactory.DEFAULT_LABELS);
    }

}
