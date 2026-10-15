package io.quarkiverse.graalpy.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Build-time configuration for the GraalPy extension.
 */
@ConfigMapping(prefix = "quarkus.graalpy")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface GraalPyBuildTimeConfig {

    /**
     * Project-relative directory holding application Python sources packaged
     * into the runtime resources (classpath).
     */
    @WithName("source-dir")
    @WithDefault("src/main/python")
    String sourceDir();

    /**
     * Project-relative directory holding test-only Python sources. Packaged
     * only when the application is built in {@code test} launch mode.
     */
    @WithName("test-source-dir")
    @WithDefault("src/test/python")
    String testSourceDir();

    /**
     * Whether to include test Python sources when running the application in
     * test mode.
     */
    @WithName("include-test-sources")
    @WithDefault("true")
    boolean includeTestSources();

    /**
     * When enabled, open a GraalPy context at build time and verify that every
     * {@code @GraalPyModuleBinding} interface points to a real Python module and
     * that every mapped function exists. Failures abort the build with the full
     * list of missing modules/functions rather than failing lazily at the first
     * runtime call.
     */
    @WithName("validate-bindings")
    @WithDefault("false")
    boolean validateBindings();
}
