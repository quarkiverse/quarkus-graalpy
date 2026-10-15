package io.quarkiverse.graalpy.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

/**
 * Runtime config for the GraalPy extension. Keys live under {@code quarkus.graalpy.*}.
 */
@ConfigMapping(prefix = "quarkus.graalpy")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface GraalPyConfig {

    /**
     * Directory prepended to {@code sys.path} at runtime, on top of the packaged sources.
     */
    Optional<String> sourcePath();
}
