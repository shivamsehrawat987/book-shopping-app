package com.bookstore.book_shopping_app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI bookStoreOpenApi() {
        String schemeName = "basicAuth";
        return new OpenAPI()
                .info(new Info().title("Book Shopping API").version("v1"))
                .components(new Components().addSecuritySchemes(schemeName,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")));
    }
}