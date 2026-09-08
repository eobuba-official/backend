package com.piggyback.backend.domain.user;

import com.piggyback.backend.common.auth.AuthenticatedUser;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.ConsultationHistoryResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddRequest;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeleteResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.MeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@Tag(name = "사용자", description = "내 정보, 보호자와 상담 내역을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 기본 정보와 등록된 보호자를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정보 조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    public ApiResponse<MeResponse> getMe(@Parameter(hidden = true) @AuthenticatedUser Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    @PostMapping("/guardians")
    @Operation(summary = "보호자 등록", description = "로그인한 사용자의 보호자 이름과 휴대전화 번호를 등록합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "보호자 등록 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "보호자 정보 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    public ApiResponse<GuardianAddResponse> addGuardian(
                                                        @Parameter(hidden = true) @AuthenticatedUser Long userId,
                                                        @RequestBody @Valid GuardianAddRequest request) {
        return ApiResponse.success(userService.addGuardian(userId, request));
    }

    @DeleteMapping("/guardians/{guardianId}")
    @Operation(summary = "보호자 삭제", description = "등록된 보호자를 사용자 계정에서 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "보호자 삭제 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "등록된 보호자를 찾을 수 없음")
    })
    public ApiResponse<GuardianDeleteResponse> deleteGuardian(
                                                              @Parameter(hidden = true) @AuthenticatedUser Long userId,
                                                              @Parameter(description = "삭제할 보호자 ID", required = true)
                                                              @PathVariable Long guardianId) {
        return ApiResponse.success(userService.deleteGuardian(userId, guardianId));
    }

    @GetMapping("/consultations")
    @Operation(summary = "상담 내역 조회", description = "로그인한 사용자의 은행 업무 상담 내역을 최신순으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상담 내역 조회 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "JWT 인증 실패")
    })
    public ApiResponse<ConsultationHistoryResponse> getConsultations(
            @Parameter(hidden = true) @AuthenticatedUser Long userId) {
        return ApiResponse.success(userService.getConsultations(userId));
    }
}
