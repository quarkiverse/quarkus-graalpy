package io.quarkiverse.graalpy.it;

import io.quarkiverse.graalpy.runtime.GraalPyModuleBinding;
import io.quarkiverse.graalpy.runtime.Prediction;

@GraalPyModuleBinding("classifier")
public interface Classifier {
    Prediction predict(float[] features);
}
