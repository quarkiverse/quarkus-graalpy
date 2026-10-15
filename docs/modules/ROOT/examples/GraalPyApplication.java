package org.acme;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.graalpy.runtime.GraalPy;
import io.quarkiverse.graalpy.runtime.Prediction;

@Path("/classify")
public class GraalPyApplication {

    public record Response(String label, double confidence) {
    }

    @Inject
    GraalPy graalPy;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response classify(float[] features) {
        Prediction prediction = graalPy.module("classifier")
                .function("predict")
                .invoke(features)
                .as(Prediction.class);
        return new Response(prediction.label(), prediction.confidence());
    }
}
