package org.testcontainers.utility;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrefixingImageNameSubstitutorTest {

    private TestcontainersConfiguration mockConfiguration;

    private PrefixingImageNameSubstitutor underTest;

    @Before
    public void setUp() {
        mockConfiguration = mock(TestcontainersConfiguration.class);
        underTest = new PrefixingImageNameSubstitutor(mockConfiguration);
    }

    @Test
    public void testHappyPath() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror/");

        final DockerImageName result = underTest.apply(DockerImageName.parse("some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is applied")
            .isEqualTo("someregistry.com/our-mirror/some/image:tag");
    }

    @Test
    public void testNormalizeToLibraryPath() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror/");
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.NORMALIZE_PROPERTY_KEY), any()))
            .thenReturn("true");

        final DockerImageName result = underTest.apply(DockerImageName.parse("image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is applied")
            .isEqualTo("someregistry.com/our-mirror/library/image:tag");
        result.assertCompatibleWith(DockerImageName.parse("image:tag"));
    }

    @Test
    public void hubIoRegistryIsNotChanged() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror/");

        final DockerImageName result = underTest.apply(DockerImageName.parse("docker.io/some/image:tag"));

        assertThat(result.asCanonicalNameString()).as("The prefix is applied").isEqualTo("docker.io/some/image:tag");
    }

    @Test
    public void hubComRegistryIsNotChanged() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror/");

        final DockerImageName result = underTest.apply(DockerImageName.parse("registry.hub.docker.com/some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is applied")
            .isEqualTo("registry.hub.docker.com/some/image:tag");
    }

    @Test
    public void thirdPartyRegistriesNotAffected() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror/");

        final DockerImageName result = underTest.apply(DockerImageName.parse("gcr.io/something/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is not applied if a third party registry is used")
            .isEqualTo("gcr.io/something/image:tag");
    }

    @Test
    public void testNoDoublePrefixing() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror/");

        final DockerImageName result = underTest.apply(DockerImageName.parse("someregistry.com/some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is not applied if already present")
            .isEqualTo("someregistry.com/some/image:tag");
    }

    @Test
    public void testHandlesEmptyValue() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("");

        final DockerImageName result = underTest.apply(DockerImageName.parse("some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is not applied if the env var is not set")
            .isEqualTo("some/image:tag");
    }

    @Test
    public void testHandlesRegistryOnlyWithTrailingSlash() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/");

        final DockerImageName result = underTest.apply(DockerImageName.parse("some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is applied")
            .isEqualTo("someregistry.com/some/image:tag");
    }

    @Test
    public void testCombinesLiterallyForRegistryOnlyWithoutTrailingSlash() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com");

        final DockerImageName result = underTest.apply(DockerImageName.parse("some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is applied")
            .isEqualTo("someregistry.comsome/image:tag");
    }

    @Test
    public void testCombinesLiterallyForBothPartsWithoutTrailingSlash() {
        when(mockConfiguration.getEnvVarOrProperty(eq(PrefixingImageNameSubstitutor.PREFIX_PROPERTY_KEY), any()))
            .thenReturn("someregistry.com/our-mirror");

        final DockerImageName result = underTest.apply(DockerImageName.parse("some/image:tag"));

        assertThat(result.asCanonicalNameString())
            .as("The prefix is applied")
            .isEqualTo("someregistry.com/our-mirrorsome/image:tag");
    }
}
