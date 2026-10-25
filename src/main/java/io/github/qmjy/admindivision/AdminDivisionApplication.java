package io.github.qmjy.admindivision;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point of the admin division extractor.
 */
@SpringBootApplication
@EnableConfigurationProperties(ExportOptions.class)
public class AdminDivisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminDivisionApplication.class, args);
    }
}
