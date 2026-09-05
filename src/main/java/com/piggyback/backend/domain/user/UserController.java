package com.piggyback.backend.domain.user;

import com.piggyback.backend.common.auth.AuthenticatedUser;
import com.piggyback.backend.common.response.ApiResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.ConsultationHistoryResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddRequest;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianAddResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.GuardianDeleteResponse;
import com.piggyback.backend.domain.user.dto.UserDtos.MeResponse;
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
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<MeResponse> getMe(@AuthenticatedUser Long userId) {
        return ApiResponse.success(userService.getMe(userId));
    }

    @PostMapping("/guardians")
    public ApiResponse<GuardianAddResponse> addGuardian(@AuthenticatedUser Long userId,
                                                        @RequestBody @Valid GuardianAddRequest request) {
        return ApiResponse.success(userService.addGuardian(userId, request));
    }

    @DeleteMapping("/guardians/{guardianId}")
    public ApiResponse<GuardianDeleteResponse> deleteGuardian(@AuthenticatedUser Long userId,
                                                              @PathVariable Long guardianId) {
        return ApiResponse.success(userService.deleteGuardian(userId, guardianId));
    }

    @GetMapping("/consultations")
    public ApiResponse<ConsultationHistoryResponse> getConsultations(@AuthenticatedUser Long userId) {
        return ApiResponse.success(userService.getConsultations(userId));
    }
}
