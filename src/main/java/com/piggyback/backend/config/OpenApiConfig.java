package com.piggyback.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 API에서 발급받은 access token을 입력하세요. 'Bearer ' 접두사는 Swagger UI가 자동으로 붙입니다."
)
public class OpenApiConfig {

    @Bean
    OpenAPI piggybackOpenApi() {
        return new OpenAPI().info(new Info()
                .title("어부바 백엔드 API")
                .description("어르신의 자연어·음성 입력을 은행 업무로 분류하고, 사기 위험을 먼저 확인한 뒤 방문 여부를 안내하는 API입니다.")
                .version("v1.2"));
    }
}
