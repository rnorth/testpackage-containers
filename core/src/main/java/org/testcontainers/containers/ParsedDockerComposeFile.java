package org.testcontainers.containers;

import com.google.common.annotations.VisibleForTesting;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Representation of a docker-compose file, with partial parsing for validation and extraction of a minimal set of
 * data.
 */
@Slf4j
@EqualsAndHashCode
class ParsedDockerComposeFile {

    private final Map<String, Object> composeFileContent;
    private final String composeFileName;

    @Getter
    private Set<String> serviceImageNames = new HashSet<>();

    ParsedDockerComposeFile(File composeFile) {
        Yaml yaml = new Yaml();
        try (FileInputStream fileInputStream = FileUtils.openInputStream(composeFile)) {
            composeFileContent = yaml.load(fileInputStream);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse YAML file from " + composeFile.getAbsolutePath(), e);
        }
        this.composeFileName = composeFile.getAbsolutePath();

        parseAndValidate();
    }

    @VisibleForTesting
    ParsedDockerComposeFile(Map<String, Object> testContent) {
        this.composeFileContent = testContent;
        this.composeFileName = "";

        parseAndValidate();
    }

    private void parseAndValidate() {
        final Map<String, ?> servicesMap;
        if (composeFileContent.containsKey("version")) {
            if ("2.0".equals(composeFileContent.get("version"))) {
                log.warn("Testcontainers may not be able to clean up networks spawned using Docker Compose v2.0 files. " +
                    "Please see https://github.com/testcontainers/moby-ryuk/issues/2, and specify 'version: \"2.1\"' or " +
                    "higher in {}", composeFileName);
            }

            final Object servicesElement = composeFileContent.get("services");
            if (servicesElement == null) {
                log.debug("Compose file {} has an unknown format: 'version' is set but 'services' is not defined", composeFileName);
                return;
            }
            if (!(servicesElement instanceof Map)) {
                log.debug("Compose file {} has an unknown format: 'services' is not Map", composeFileName);
                return;
            }

            servicesMap = (Map<String, ?>) servicesElement;
        } else {
            servicesMap = composeFileContent;
        }

        for (Map.Entry<String, ?> entry : servicesMap.entrySet()) {
            String serviceName = entry.getKey();
            Object serviceDefinition = entry.getValue();
            if (!(serviceDefinition instanceof Map)) {
                log.debug("Compose file {} has an unknown format: service '{}' is not Map", composeFileName, serviceName);
                break;
            }

            final Map serviceDefinitionMap = (Map) serviceDefinition;
            if (serviceDefinitionMap.containsKey("container_name")) {
                throw new IllegalStateException(String.format(
                    "Compose file %s has 'container_name' property set for service '%s' but this property is not supported by Testcontainers, consider removing it",
                    composeFileName,
                    serviceName
                ));
            }
            if (serviceDefinitionMap.containsKey("image") && serviceDefinitionMap.get("image") instanceof String) {
                serviceImageNames.add((String) serviceDefinitionMap.get("image"));
            }
        }
    }
}
