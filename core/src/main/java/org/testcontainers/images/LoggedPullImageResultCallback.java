package org.testcontainers.images;

import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import org.slf4j.Logger;
import org.testcontainers.utility.ImagePullCountLogger;

import java.io.Closeable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

/**
 * {@link PullImageResultCallback} with improved logging of pull progress.
 */
class LoggedPullImageResultCallback extends PullImageResultCallback {
    private final Logger logger;
    private final String canonicalImageName;

    private final Set<String> allLayers = new HashSet<>();
    private final Set<String> downloadedLayers = new HashSet<>();
    private final Set<String> pulledLayers = new HashSet<>();
    private final Map<String, Long> totalSizes = new HashMap<>();
    private final Map<String, Long> currentSizes = new HashMap<>();
    private boolean completed;
    private Instant start;

    LoggedPullImageResultCallback(final Logger logger, final String canonicalImageName) {
        this.logger = logger;
        this.canonicalImageName = canonicalImageName;
    }

    @Override
    public void onStart(final Closeable stream) {
        super.onStart(stream);
        start = Instant.now();

        logger.info("Starting to pull image");
    }

    @Override
    public void onNext(final PullResponseItem item) {
        super.onNext(item);

        final String statusLowercase = item.getStatus() != null ? item.getStatus().toLowerCase() : "";
        final String id = item.getId();

        if (item.getProgressDetail() != null) {
            allLayers.add(id);
        }

        if (statusLowercase.equalsIgnoreCase("download complete")) {
            downloadedLayers.add(id);
        }

        if (statusLowercase.equalsIgnoreCase("pull complete")) {
            pulledLayers.add(id);
        }

        if (item.getProgressDetail() != null) {
            Long total = item.getProgressDetail().getTotal();
            Long current = item.getProgressDetail().getCurrent();

            if (total != null && total > totalSizes.getOrDefault(id, 0L)) {
                totalSizes.put(id, total);
            }
            if (current != null && current > currentSizes.getOrDefault(id, 0L)) {
                currentSizes.put(id, current);
            }
        }

        if (statusLowercase.startsWith("pulling from" ) || statusLowercase.contains("complete" )) {

            long totalSize = totalLayerSize();
            long currentSize = downloadedLayerSize();

            int pendingCount = allLayers.size() - downloadedLayers.size();
            String friendlyTotalSize;
            if (pendingCount > 0) {
                friendlyTotalSize = "? MB";
            } else {
                friendlyTotalSize = byteCountToDisplaySize(totalSize);
            }

            logger.info("Pulling image layers: {} pending, {} downloaded, {} extracted, ({}/{})",
                format("%2d", pendingCount),
                format("%2d", downloadedLayers.size()),
                format("%2d", pulledLayers.size()),
                byteCountToDisplaySize(currentSize),
                friendlyTotalSize);
        }

        if (statusLowercase.contains("complete")) {
            completed = true;
        }
    }

    @Override
    public void onComplete() {
        super.onComplete();

        final long downloadedLayerSize = downloadedLayerSize();
        final long duration = Duration.between(start, Instant.now()).getSeconds();

        if (completed) {
            logger.info("Pull complete. {} layers, pulled in {}s (downloaded {} at {}/s)",
                allLayers.size(),
                duration,
                byteCountToDisplaySize(downloadedLayerSize),
                byteCountToDisplaySize(downloadedLayerSize / duration));
        }

        ImagePullCountLogger.instance().recordPull(canonicalImageName);
    }

    private long downloadedLayerSize() {
        return currentSizes.values().stream().filter(Objects::nonNull).mapToLong(it -> it).sum();
    }

    private long totalLayerSize() {
        return totalSizes.values().stream().filter(Objects::nonNull).mapToLong(it -> it).sum();
    }
}
