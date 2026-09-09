package com.piggyback.backend.recommendation.domain;

import com.piggyback.backend.common.exception.BusinessException;
import com.piggyback.backend.common.exception.ErrorCode;

/**
 * 지점 조회 위치 조건. GPS 좌표(lat/lng) 또는 법정동코드(regionCode) 중 하나로 만든다.
 */
public record LocationQuery(Double lat, Double lng, String regionCode) {

    public static LocationQuery of(Double lat, Double lng, String regionCode) {
        boolean hasLat = lat != null;
        boolean hasLng = lng != null;
        boolean hasRegion = regionCode != null && !regionCode.isBlank();

        if (hasLat != hasLng) {
            throw invalidInput("lat과 lng는 함께 입력해야 합니다.");
        }
        if (!hasLat && !hasRegion) {
            throw invalidInput("lat/lng 또는 regionCode 중 하나는 필수입니다.");
        }
        if (hasLat && (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180)) {
            throw invalidInput("위도 또는 경도 범위가 올바르지 않습니다.");
        }
        if (!hasLat && !regionCode.matches("\\d{10}")) {
            throw invalidInput("regionCode는 숫자 10자리여야 합니다.");
        }
        return hasLat
                ? new LocationQuery(lat, lng, null)
                : new LocationQuery(null, null, regionCode);
    }

    public boolean isGpsBased() {
        return lat != null;
    }

    private static BusinessException invalidInput(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
}
