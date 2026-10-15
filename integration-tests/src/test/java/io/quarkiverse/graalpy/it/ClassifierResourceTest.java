package io.quarkiverse.graalpy.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ClassifierResourceTest {

    @Test
    void positiveFeaturesReturnPositiveLabel() {
        given()
                .contentType("application/json")
                .body("[0.2, -0.1, 0.9]")
                .when().post("/classify")
                .then()
                .statusCode(200)
                .body("label", equalTo("positive"));
    }

    @Test
    void negativeFeaturesReturnNegativeLabel() {
        given()
                .contentType("application/json")
                .body("[-1.0, -2.0]")
                .when().post("/classify")
                .then()
                .statusCode(200)
                .body("label", equalTo("negative"));
    }
}
