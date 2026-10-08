package rw.ikimina.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document is the API contract (spec 4.2, 17). CI exports it on every
 * build; from Phase 1 a diff against the previous version flags breaking changes.
 * Served only where {@code springdoc.api-docs.enabled} is true (not in production).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI ikiminaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ikimina API")
                .version("v1")
                .description("Multi-tenant savings-group platform. Errors are RFC 7807 problem documents "
                        + "carrying a stable `code`; money is serialised as a decimal string in RWF."));
    }
}
