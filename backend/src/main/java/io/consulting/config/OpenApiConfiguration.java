package io.consulting.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI documentation() {
        return new OpenAPI()
            .info(new Info().title("Consulting Invoice Manager").version("1.0.0"))
            .components(
                new Components().addSecuritySchemes(
                    "localToken",
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("localToken"));
    }
}
