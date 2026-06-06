package com.ohgiraffers.dalryeo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

abstract class SwaggerEndpointTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    ResponseEntity<String> get(String path) {
        return restTemplate.getForEntity(path, String.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    static class TestApplication {

        @Bean
        SampleController sampleController() {
            return new SampleController();
        }
    }

    @RestController
    static class SampleController {

        @GetMapping("/sample")
        String sample() {
            return "sample";
        }
    }
}
