package com.infolinks.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.infolinks.config",
        "com.infolinks.controllers",
        "com.infolinks.services",
})
public class InfolinksApp {

    public static void main(String[] args) {
        SpringApplication.run(InfolinksApp.class, args);
    }

}
