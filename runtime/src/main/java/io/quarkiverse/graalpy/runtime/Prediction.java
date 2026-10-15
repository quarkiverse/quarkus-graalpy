package io.quarkiverse.graalpy.runtime;

public interface Prediction {
    String label();

    double confidence();
}
