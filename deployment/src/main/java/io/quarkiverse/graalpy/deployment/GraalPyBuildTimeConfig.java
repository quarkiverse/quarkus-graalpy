package io.quarkiverse.graalpy.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Build-time config for the GraalPy extension.
 */
@ConfigMapping(prefix = "quarkus.graalpy")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface GraalPyBuildTimeConfig {

    /**
     * Directory with application Python sources, packaged into the runtime classpath.
     */
    @WithName("source-dir")
    @WithDefault("src/main/python")
    String sourceDir();

    /**
     * Directory with test-only Python sources, packaged in {@code test} launch mode.
     */
    @WithName("test-source-dir")
    @WithDefault("src/test/python")
    String testSourceDir();

    /**
     * Whether to include test Python sources when running tests.
     */
    @WithName("include-test-sources")
    @WithDefault("true")
    boolean includeTestSources();

    /**
     * If true, verify every {@code @GraalPyModuleBinding} at build time and fail
     * the build on missing modules or functions.
     */
    @WithName("validate-bindings")
    @WithDefault("false")
    boolean validateBindings();
}
