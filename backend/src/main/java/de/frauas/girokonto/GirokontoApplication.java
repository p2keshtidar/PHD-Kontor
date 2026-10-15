package de.frauas.girokonto;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Anmeldung läuft über eigene JWT-Tokens: Spring soll kein Standard-Passwort erzeugen und ins Log schreiben
@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class GirokontoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GirokontoApplication.class, args);
    }
}
