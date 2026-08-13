package com.aibuilder.lovableclone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * App ka entry point — yahi se Spring Boot start hota hai.
 *
 * @SpringBootApplication = 3 cheezein ek saath:
 * 1. @Configuration  → yeh class config hai
 * 2. @EnableAutoConfiguration → Spring Boot auto setup (Tomcat, JSON, etc.)
 * 3. @ComponentScan → isi package aur neeche ke packages mein beans dhoondhta hai
 */
@SpringBootApplication
public class LovableCloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(LovableCloneApplication.class, args);
    }
}
