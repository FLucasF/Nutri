package br.com.nutriplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class NutriPlanApplication {
    public static void main(String[] args) {
        SpringApplication.run(NutriPlanApplication.class, args);
    }
}
