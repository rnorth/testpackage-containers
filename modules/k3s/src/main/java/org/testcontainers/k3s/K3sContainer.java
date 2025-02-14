package org.testcontainers.k3s;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.SaveImageCmd;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Testcontainers implementation for K3S
 * <p>
 * Supported image: {@code rancher/k3s}
 */
public class K3sContainer extends GenericContainer<K3sContainer> {

    public static int KUBE_SECURE_PORT = 6443;

    public static int RANCHER_WEBHOOK_PORT = 8443;

    private String kubeConfigYaml;

    public K3sContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DockerImageName.parse("rancher/k3s"));

        addExposedPorts(KUBE_SECURE_PORT, RANCHER_WEBHOOK_PORT);
        setPrivilegedMode(true);
        withCreateContainerCmdModifier(it -> {
            it.getHostConfig().withCgroupnsMode("host");
        });
        addFileSystemBind("/sys/fs/cgroup", "/sys/fs/cgroup", BindMode.READ_WRITE);

        Map<String, String> tmpFsMapping = new HashMap<>();
        tmpFsMapping.put("/run", "");
        tmpFsMapping.put("/var/run", "");
        setTmpFsMapping(tmpFsMapping);

        setCommand("server", "--disable=traefik", "--tls-san=" + this.getHost());
        setWaitStrategy(Wait.forLogMessage(".*Node controller sync successful.*", 1));
    }

    /**
     * import images into the running k3s instance. Images are assumed to be present
     * on the host already.
     *
     * @param images images to be imported into the k3s.
     * @throws IOException in case temporary directory/files needed for the import can not be created
     * @throws InterruptedException thrown when the thread is interrupted
     */
    public void loadImages(Set<String> images) throws IOException, InterruptedException {
        if (images != null && !images.isEmpty()) {
            // create temporary directory on the host where all tar(s) will reside
            File tarsTempFolder = Files.createTempDirectory("").toFile();
            File tarFile = File.createTempFile("images", ".tar", tarsTempFolder);

            for (String image : images) {
                try (SaveImageCmd saveImageCmd = getDockerClient().saveImageCmd(image)) {
                    InputStream imageStream = saveImageCmd.exec();
                    // tar file for a single image
                    Files.copy(imageStream, tarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    importTar(tarFile, true);
                }
            }

            // at this point, directory is empty
            Files.deleteIfExists(tarsTempFolder.toPath());
        }
    }

    /**
     * Import this tar file into the running k3s.
     *
     * @param paths absolute paths of the tar files on the host.
     * @throws IOException in case temporary directory/files needed for the import can not be created
     * @throws InterruptedException thrown when the thread is interrupted
     */
    public void loadImageFromTar(Set<String> paths) throws IOException, InterruptedException {
        if (paths != null && !paths.isEmpty()) {
            for (String path : paths) {
                File tarFile = new File(path);
                importTar(tarFile, false);
            }
        }
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        String rawKubeConfig = copyFileFromContainer(
            "/etc/rancher/k3s/k3s.yaml",
            is -> IOUtils.toString(is, StandardCharsets.UTF_8)
        );
        String serverUrl = "https://" + this.getHost() + ":" + this.getMappedPort(KUBE_SECURE_PORT);
        kubeConfigYaml = kubeConfigWithServerUrl(rawKubeConfig, serverUrl);
    }

    /**
     * Return the kubernetes client configuration to access k3s from the host machine.
     *
     * @return the kubeConfig yaml.
     */
    public String getKubeConfigYaml() {
        return kubeConfigYaml;
    }

    /**
     * Generate a kubernetes client configuration for use on a docker internal network. The kubeConfig can be used by
     * another docker container running in the same network as the k3s container. For access from the host, use
     * the {@link #getKubeConfigYaml()} method instead.
     *
     * @param networkAlias a valid network alias of the k3s container.
     * @return the kubeConfig yaml.
     */
    public String generateInternalKubeConfigYaml(String networkAlias) {
        if (this.getNetworkAliases().contains(networkAlias)) {
            String serverUrl = "https://" + networkAlias + ":" + KUBE_SECURE_PORT;
            return kubeConfigWithServerUrl(kubeConfigYaml, serverUrl);
        } else {
            throw new IllegalArgumentException(networkAlias + " is not a network alias for k3s container");
        }
    }

    @SneakyThrows
    private String kubeConfigWithServerUrl(String kubeConfigYaml, String serverUrl) {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        ObjectNode kubeConfigObjectNode = objectMapper.readValue(kubeConfigYaml, ObjectNode.class);

        JsonNode clusterNode = kubeConfigObjectNode.at("/clusters/0/cluster");
        if (!clusterNode.isObject()) {
            throw new IllegalStateException("'/clusters/0/cluster' expected to be an object");
        }
        ObjectNode clusterConfig = (ObjectNode) clusterNode;
        clusterConfig.replace("server", new TextNode(serverUrl));

        kubeConfigObjectNode.set("current-context", new TextNode("default"));

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(kubeConfigObjectNode);
    }

    private void importTar(File tarFile, boolean deleteAfterCopy) throws IOException, InterruptedException {
        try (CopyArchiveToContainerCmd copyCmd = getDockerClient().copyArchiveToContainerCmd(getContainerId())) {
            String remotePathName = "/tmp/" + tarFile.getName();
            copyCmd.withHostResource(tarFile.getAbsolutePath()).withRemotePath("/tmp").exec();
            Container.ExecResult result = execInContainer("ctr", "i", "import", remotePathName);
            boolean noErrors = result.getStderr() == null || result.getStderr().isEmpty();
            if (!noErrors) {
                throw new RuntimeException(result.getStderr());
            }
        } finally {
            if (deleteAfterCopy) {
                Files.deleteIfExists(tarFile.toPath());
            }
        }
    }
}
