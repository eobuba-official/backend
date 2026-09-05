package com.piggyback.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI piggybackOpenApi() {
        return new OpenAPI().info(new Info()
                .title("어부바 백엔드 API")
                .description("어르신의 은행 업무 부담을 덜어주는 어부바 API")
                .version("v1.2"));
    }
}
