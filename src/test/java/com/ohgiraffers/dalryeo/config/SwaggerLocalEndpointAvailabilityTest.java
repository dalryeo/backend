package com.ohgiraffers.dalryeo.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = SwaggerEndpointTestSupport.TestApplication.class
)
@ActiveProfiles("local")
class SwaggerLocalEndpointAvailabilityTest extends SwaggerEndpointTestSupport {

    @Test
    void localProfileServesOpenApiDocs() {
        ResponseEntity<String> response = get("/v3/api-docs");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"openapi\"");
    }

    @Test
    void localProfileServesSwaggerUi() {
        ResponseEntity<String> response = get("/swagger-ui/index.html");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Swagger UI");
    }
}
