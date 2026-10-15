package io.quarkiverse.graalpy.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Runtime configuration for the GraalPy extension.
 *
 * All keys live under the {@code quarkus.graalpy.*} namespace.
 */
@ConfigMapping(prefix = "quarkus.graalpy")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface GraalPyConfig {

    /**
     * Filesystem directory to prepend to {@code sys.path} at runtime, in addition
     * to the Python sources packaged at build time. Useful in dev mode when the
     * project base directory is accessible and you want edits to take effect
     * without rebuilding resources.
     */
    Optional<String> sourcePath();
}
