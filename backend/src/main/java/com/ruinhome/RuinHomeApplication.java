package com.ruinhome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RuinHomeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuinHomeApplication.class, args);
    }
}
