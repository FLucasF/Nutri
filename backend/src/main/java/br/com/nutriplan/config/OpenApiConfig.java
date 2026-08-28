package br.com.nutriplan.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openApi() {
        final String schema = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("NutriPlan API")
                        .version("0.1.0")
                        // The Swagger description is API documentation, read by whoever
                        // integrates — it is not a nutritionist's or a patient's
                        // screen.
                        .description("Practice management software for nutritionists"))
                .addSecurityItem(new SecurityRequirement().addList(schema))
                .components(new Components().addSecuritySchemes(schema,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
