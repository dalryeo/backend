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
@ActiveProfiles("swagger-test")
class SwaggerDefaultEndpointAvailabilityTest extends SwaggerEndpointTestSupport {

    @Test
    void defaultProfileDoesNotServeOpenApiDocs() {
        ResponseEntity<String> response = get("/v3/api-docs");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void defaultProfileDoesNotServeSwaggerUi() {
        ResponseEntity<String> response = get("/swagger-ui/index.html");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
