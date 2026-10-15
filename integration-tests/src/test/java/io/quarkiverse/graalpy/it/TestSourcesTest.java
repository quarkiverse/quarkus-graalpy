package io.quarkiverse.graalpy.it;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import io.quarkiverse.graalpy.runtime.GraalPy;
import io.quarkiverse.graalpy.runtime.Prediction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies that modules placed under {@code src/test/python} are picked up and
 * added to {@code sys.path} when the application runs in test mode.
 */
@QuarkusTest
class TestSourcesTest {

    @Inject
    GraalPy graalPy;

    @Test
    void testOnlyModuleIsImportable() {
        Prediction prediction = graalPy.module("fixtures")
                .function("canned_prediction")
                .invoke()
                .as(Prediction.class);
        Assertions.assertEquals("canned", prediction.label());
        Assertions.assertEquals(0.42, prediction.confidence(), 1e-6);
    }
}
