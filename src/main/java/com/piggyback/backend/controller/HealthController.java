package com.piggyback.backend.controller;

import com.piggyback.backend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "서버 상태", description = "백엔드 서버가 정상적으로 응답하는지 확인합니다.")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "서버 상태 확인", description = "인증 없이 백엔드 서버의 실행 상태를 확인합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "서버 정상 동작")
    })
    public ApiResponse<String> health() {
        return ApiResponse.success("Piggyback backend is running");
    }
}
