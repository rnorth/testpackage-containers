package org.testcontainers.providers.kubernetes.execution;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

public class KubernetesExecutionLogAdapter<T extends ResultCallback<Frame>> extends OutputStream {
    private final StreamType streamType;
    private final T resultCallback;

    public KubernetesExecutionLogAdapter(StreamType streamType, T resultCallback) {
        this.streamType = streamType;
        this.resultCallback = resultCallback;
    }

    @Override
    public void write(int b) throws IOException {
        resultCallback.onNext(new Frame(streamType, new byte[]{ (byte) b }));
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
        resultCallback.onNext(new Frame(streamType, b));
    }

    @Override
    public void close() throws IOException {
        resultCallback.onComplete();
        resultCallback.close();
        super.close();
    }
}
