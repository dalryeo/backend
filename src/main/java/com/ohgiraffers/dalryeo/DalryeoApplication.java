package com.ohgiraffers.dalryeo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DalryeoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DalryeoApplication.class, args);
    }

}
