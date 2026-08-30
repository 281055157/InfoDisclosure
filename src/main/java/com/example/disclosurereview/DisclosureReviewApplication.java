package com.example.disclosurereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DisclosureReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(DisclosureReviewApplication.class, args);
    }
}
