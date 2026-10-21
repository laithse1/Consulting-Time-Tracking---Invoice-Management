package io.consulting;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApi {

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
