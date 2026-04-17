package com.marioreis.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ledger Command Service API")
                        .version("1.0.0")
                        .description("CQRS write-side ledger command service for managing financial accounts, transfers, and transaction state. " +
                                "All mutating endpoints require an Idempotency-Key header for idempotent execution.")
                        .contact(new Contact()
                                .name("Mario Soares Reis")
                                .url("https://github.com/mariosoaresreis/ledger")
                                .email("mario@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://34.8.164.128")
                                .description("GKE DEV"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development")));
    }
}

