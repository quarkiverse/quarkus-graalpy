package io.quarkiverse.graalpy.it;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.graalpy.runtime.Prediction;

@Path("/classify")
public class ClassifierResource {

    public record Response(String label, double confidence) {
    }

    @Inject
    Classifier classifier;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response classify(float[] features) {
        Prediction prediction = classifier.predict(features);
        return new Response(prediction.label(), prediction.confidence());
    }
}
