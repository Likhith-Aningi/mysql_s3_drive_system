package com.drive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DriveSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriveSystemApplication.class, args);
    }
}
