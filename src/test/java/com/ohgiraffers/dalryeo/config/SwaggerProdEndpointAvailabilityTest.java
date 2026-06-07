package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = SwaggerEndpointTestSupport.TestApplication.class
)
@ActiveProfiles({"prod", "swagger-test"})
class SwaggerProdEndpointAvailabilityTest extends SwaggerEndpointTestSupport {

    @Test
    void prodProfileDoesNotServeOpenApiDocs() {
        ResponseEntity<String> response = get("/v3/api-docs");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void prodProfileDoesNotServeSwaggerUi() {
        ResponseEntity<String> response = get("/swagger-ui/index.html");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
