package com.example.github;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "GitHub Repository Search Service",
        version = "1.0",
        description = "Searches GitHub repositories, stores them in PostgreSQL and serves them "
                + "with filtering, sorting and paging."))
public class OpenApiConfig {
}
