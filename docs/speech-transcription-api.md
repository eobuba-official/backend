# 음성 인식 백엔드 연동 계약

## 처리 흐름

1. 프론트는 Web Speech API로 임시 문장을 표시하고 MediaRecorder로 음성을 녹음한다.
2. 프론트는 음성 파일과 선택적인 `browserTranscript`를 백엔드에 전송한다.
3. 백엔드는 파일을 검증한 뒤 CLOVA CSR 단문 인식 API를 호출한다.
4. CLOVA가 성공하면 `CLOVA_CSR`, CLOVA가 실패하고 브라우저 문장이 있으면
   `WEB_SPEECH_FALLBACK`으로 응답한다.
5. 프론트는 반환된 문장을 `POST /api/v1/analyze`에 전달한다.
6. Gemini가 음성 문장을 실제로 보정하면 백엔드는 최종 안내 대신 보정 확인 필요 상태를 반환한다.
7. 사용자는 보정 문장을 승인하거나 직접 수정한 뒤 보정 확인 API로 동일 상담을 확정한다.

서버는 업로드된 음성과 변환된 문장을 별도 저장하지 않으며 음성 바이트는 요청 처리 후 지운다.

## 음성 인식 API

```http
POST /api/v1/speech/transcriptions
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `audio` | file | Y | CLOVA CSR 지원 음성 파일 |
| `browserTranscript` | string | N | Web Speech API가 만든 임시 문장, 최대 1,000자 |

### 지원 포맷과 제한

- 최대 파일 크기: 3MB
- 최대 재생 시간: 60초(CLOVA CSR 제한)
- 지원 코덱/포맷: MP3, AAC, AC3, OGG, FLAC, WAV
- 지원 Content-Type: `audio/mpeg`, `audio/mp3`, `audio/aac`, `audio/x-aac`,
  `audio/ac3`, `audio/x-ac3`, `audio/ogg`, `application/ogg`, `audio/flac`,
  `audio/x-flac`, `audio/wav`, `audio/wave`, `audio/x-wav`, `audio/vnd.wave`

현재 CSR 단문 인식 API는 WebM과 MP4 컨테이너를 직접 지원하지 않는다. 프론트에서
MediaRecorder를 사용할 때는 브라우저별 녹음 포맷을 확인하고, 지원 포맷으로 변환한 파일을
전송하거나 Web Speech 임시 문장을 폴백으로 사용해야 한다.

### 요청 예시

```bash
curl -X POST http://localhost:8080/api/v1/speech/transcriptions \
  -H "Authorization: Bearer {accessToken}" \
  -F "audio=@speech.wav;type=audio/wav" \
  -F "browserTranscript=통장을 다시 만들고 시퍼"
```

### CLOVA 성공 응답

```json
{
  "success": true,
  "data": {
    "transcript": "통장을 다시 만들고 싶어",
    "source": "CLOVA_CSR",
    "browserTranscript": "통장을 다시 만들고 시퍼",
    "sttConfidence": null,
    "recheckNeeded": true
  },
  "error": null
}
```

### Web Speech 폴백 응답

CLOVA 호출이 실패했지만 `browserTranscript`가 있으면 HTTP 200으로 폴백 결과를 반환한다.

```json
{
  "success": true,
  "data": {
    "transcript": "통장을 다시 만들고 시퍼",
    "source": "WEB_SPEECH_FALLBACK",
    "browserTranscript": "통장을 다시 만들고 시퍼",
    "sttConfidence": null,
    "recheckNeeded": true
  },
  "error": null
}
```

## 업무 분류 연결

프론트는 음성 인식 결과를 아래처럼 전달한다. CLOVA CSR은 confidence를 제공하지 않으므로
`sttConfidence`는 `null`이다. Gemini 보정이 발생하면 이후 보정 확인 API에서 사용자가 승인하거나
직접 수정한다.

```http
POST /api/v1/analyze
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "utterance": "통장을 다시 만들고 싶어",
  "inputMethod": "VOICE",
  "sttConfidence": null
}
```

음성 입력은 confidence와 관계없이 `classification.sttRecheckNeeded`가 `true`로 반환된다.

Gemini가 음성 원문을 실제로 수정한 경우 `/analyze`는 업무 분류 결과를 바로 확정하지 않고
다음처럼 사용자 확인을 요청한다. 이때 분류 불가용 최상위 `guidance`는 반환하지 않는다.

```json
{
  "success": true,
  "data": {
    "consultationId": "2b2076d7-ea1c-496b-86e3-534e1bf37389",
    "status": "CORRECTION_CONFIRMATION_REQUIRED",
    "classification": {
      "status": "PENDING_CONFIRMATION",
      "originalUtterance": "셀픽스 의료진 등 좋은 울려 버렸어 아동 장 풍전",
      "correctedUtterance": "통장을 잃어버려서 다시 만들고 싶어",
      "correctionApplied": true,
      "confidence": null,
      "task": null,
      "candidates": [],
      "sttRecheckNeeded": true
    },
    "visitDecision": null,
    "guidance": null
  },
  "error": null
}
```

## Gemini 보정 문장 확인 API

사용자가 Gemini 보정 문장을 승인하거나 직접 수정한 문장을 동일 상담에 반영한다.
`taskTypeCode`를 생략하면 확정 문장을 다시 분류하고, 사용자가 업무를 직접 선택했다면
허용된 8종 업무 코드를 함께 전달한다. 업무를 직접 선택해도 사기 검사는 항상 우선한다.

```http
POST /api/v1/consultations/{consultationId}/correction-confirmation
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "confirmedUtterance": "통장을 잃어버려서 다시 만들고 싶어",
  "taskTypeCode": "PASSBOOK_REISSUE"
}
```

`taskTypeCode`는 선택값이다. 사용자가 보정 문장만 승인하거나 직접 수정했다면 아래처럼 문장만
전달한다.

```json
{
  "confirmedUtterance": "통장을 잃어버려서 다시 만들고 싶어"
}
```

업무가 확정되면 새로운 상담을 만들지 않고 기존 `consultationId`를 갱신한다.

```json
{
  "success": true,
  "data": {
    "consultationId": "2b2076d7-ea1c-496b-86e3-534e1bf37389",
    "status": "TASK_CONFIRMED",
    "classification": {
      "status": "CONFIRMED",
      "originalUtterance": "셀픽스 의료진 등 좋은 울려 버렸어 아동 장 풍전",
      "correctedUtterance": "통장을 잃어버려서 다시 만들고 싶어",
      "correctionApplied": true,
      "confidence": 0.4,
      "task": {
        "taskTypeCode": "PASSBOOK_REISSUE",
        "name": "통장 재발급",
        "easyDescription": "통장을 잃어버렸을 때 새로 만드는 일"
      },
      "candidates": [],
      "sttRecheckNeeded": false
    },
    "visitDecision": {
      "decision": "VISIT_REQUIRED",
      "reason": "통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다.",
      "remoteMethods": [],
      "officialChannels": []
    },
    "guidance": null
  },
  "error": null
}
```

보정 확인 API는 `CORRECTION_CONFIRMATION_REQUIRED` 응답을 받은 상담에서만 한 번 처리할 수 있다.
다른 사용자의 상담은 `404`, 이미 처리됐거나 확인할 수 없는 상태는 `409`를 반환한다.

## 오류 코드

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_AUDIO` | 파일 누락, 빈 파일, 미지원 MIME, 파일 시그니처 불일치 |
| 400 | `INVALID_INPUT` | `browserTranscript`가 1,000자를 초과 |
| 401 | `UNAUTHORIZED` | 인증 토큰 누락 또는 오류 |
| 413 | `AUDIO_TOO_LARGE` | 3MB 초과 |
| 502 | `STT_ERROR` | CLOVA 인증·할당량·서버 오류, 타임아웃, 응답 파싱 실패 |

보정 확인 API에서는 다음 오류를 추가로 반환한다.

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_INPUT` | 확정 문장이 비었거나 1,000자를 초과 |
| 401 | `UNAUTHORIZED` | 인증 토큰 누락 또는 오류 |
| 404 | `CONSULTATION_NOT_FOUND` | 본인 소유의 상담이 없음 |
| 404 | `TASK_TYPE_NOT_FOUND` | 허용되지 않은 업무 코드 |
| 409 | `INVALID_STATE` | 보정 확인 대상이 아니거나 이미 처리된 상담 |
| 502 | `LLM_ERROR` | 확정 문장 재분석 실패 |

`browserTranscript`가 제공되면 CLOVA에서 발생한 `INVALID_AUDIO`, `AUDIO_TOO_LARGE`,
`STT_ERROR` 대신 Web Speech 폴백 응답을 우선 반환한다. 백엔드 자체 파일 검증에서 거부된
요청은 폴백하지 않는다.
