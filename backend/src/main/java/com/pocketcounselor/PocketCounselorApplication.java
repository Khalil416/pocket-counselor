package com.pocketcounselor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application Class
 * 
 * This is the entry point of our application.
 * Spring Boot will automatically configure everything when this runs.
 */
@SpringBootApplication
public class PocketCounselorApplication {

    public static void main(String[] args) {
        // Start the Spring Boot application
        SpringApplication.run(PocketCounselorApplication.class, args);
        System.out.println(" Pocket Counselor backend is running!");
        System.out.println(" API available at: http://localhost:8080/api/analyze");
    }
}

