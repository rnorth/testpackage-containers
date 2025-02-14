package org.testcontainers.k3s;

import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.SaveImageCmd;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class K3sLoadImagesTest {

    @ClassRule
    public static K3sContainer k3s = new K3sContainer(DockerImageName.parse("rancher/k3s:v1.21.3-k3s1"));

    @Test
    public void testLoadImages() throws Exception {
        try (PullImageCmd pullImageCmd = k3s.getDockerClient().pullImageCmd("busybox:1.35")) {
            pullImageCmd.start().awaitCompletion();

            k3s.loadImages(Collections.singleton("busybox:1.35"));
            Container.ExecResult result = k3s.execInContainer("crictl", "images");
            assertThat(result.getStdout()).contains("busybox");
        }
    }

    @Test
    public void testLoadImageFromTar() throws Exception {
        try (PullImageCmd pullImageCmd = k3s.getDockerClient().pullImageCmd("busybox:1.35")) {
            pullImageCmd.start().awaitCompletion();

            File tarsTempFolder = Files.createTempDirectory("").toFile();
            File tarFile = File.createTempFile("images", ".tar", tarsTempFolder);

            try (SaveImageCmd saveImageCmd = k3s.getDockerClient().saveImageCmd("busybox:1.35")) {
                InputStream imageStream = saveImageCmd.exec();
                Files.copy(imageStream, tarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                k3s.loadImageFromTar(Collections.singleton(tarFile.getAbsolutePath()));

                Container.ExecResult result = k3s.execInContainer("crictl", "images");
                assertThat(result.getStdout()).contains("busybox");
            } finally {
                Files.delete(tarFile.toPath());
                Files.delete(tarsTempFolder.toPath());
            }
        }
    }
}
