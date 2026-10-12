package de.frauas.girokonto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GirokontoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GirokontoApplication.class, args);
    }
}
