package org.testcontainers.images;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.testcontainers.utility.Base58;

import java.util.concurrent.CompletableFuture;

public class RemoteDockerImageTest {

    @Test
    public void toStringContainsOnlyImageName() {
        String imageName = Base58.randomString(8).toLowerCase();
        RemoteDockerImage remoteDockerImage = new RemoteDockerImage(imageName);
        assertThat(remoteDockerImage.toString(), containsString("imageName=" + imageName));
    }

    @Test
    public void toStringWithExceptionContainsOnlyImageNameFuture()  {
        CompletableFuture<String> imageNameFuture = new CompletableFuture<>();
        imageNameFuture.completeExceptionally(new RuntimeException("arbitrary"));

        RemoteDockerImage remoteDockerImage = new RemoteDockerImage(imageNameFuture);
        assertThat(remoteDockerImage.toString(), containsString("imageName=<resolving>"));
    }

    @Test(timeout=5000L)
    public void toStringDoesntResolveImageNameFuture()  {
        CompletableFuture<String> imageNameFuture = new CompletableFuture<>();

        // verify that we've set up the test properly
        assertFalse(imageNameFuture.isDone());

        RemoteDockerImage remoteDockerImage = new RemoteDockerImage(imageNameFuture);
        assertThat(remoteDockerImage.toString(), containsString("imageName=<resolving>"));

        // Make sure the act of calling toString doesn't resolve the imageNameFuture
        assertFalse(imageNameFuture.isDone());

        String imageName = Base58.randomString(8).toLowerCase();
        imageNameFuture.complete(imageName);
        assertThat(remoteDockerImage.toString(), containsString("imageName=" + imageName));
    }
}
