package io.quarkiverse.graalpy.it;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.graalpy.runtime.Prediction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ClassifierTest {

    @Inject
    Classifier classifier;

    @Test
    void injectedBindingReturnsPositivePrediction() {
        Prediction prediction = classifier.predict(new float[] { 1.0f, 2.0f, 3.0f });
        Assertions.assertEquals("positive", prediction.label());
        Assertions.assertTrue(prediction.confidence() > 0.0);
    }

    @Test
    void negativeFeaturesReturnNegativeLabel() {
        Prediction prediction = classifier.predict(new float[] { -5.0f, -1.0f });
        Assertions.assertEquals("negative", prediction.label());
    }
}
