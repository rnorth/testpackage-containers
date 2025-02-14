package org.testcontainers.cloudflare;

import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public class CloudflaredContainer extends GenericContainer<CloudflaredContainer> {

    private String publicUrl;

    public CloudflaredContainer(DockerImageName dockerImageName, int port) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DockerImageName.parse("cloudflare/cloudflared"));
        withAccessToHost(true);
        Testcontainers.exposeHostPorts(port);
        withCommand("tunnel", "--url", String.format("http://host.testcontainers.internal:%d", port));
        waitingFor(Wait.forLogMessage(".*Registered tunnel connection.*", 1));
    }

    public String getPublicUrl() {
        if (null != publicUrl) {
            return publicUrl;
        }
        String logs = getLogs();
        String[] split = logs.split(String.format("%n"));
        boolean found = false;
        for (int i = 0; i < split.length; i++) {
            String currentLine = split[i];
            if (currentLine.contains("Your quick Tunnel has been created")) {
                found = true;
                continue;
            }
            if (found) {
                return publicUrl = currentLine.substring(currentLine.indexOf("http"), currentLine.indexOf(".com") + 4);
            }
        }
        throw new IllegalStateException("Didn't find public url in logs. Has container started?");
    }
}
