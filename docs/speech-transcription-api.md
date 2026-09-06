# 음성 인식 백엔드 연동 계약

## 처리 흐름

1. 프론트는 Web Speech API로 임시 문장을 표시하고 MediaRecorder로 음성을 녹음한다.
2. 프론트는 음성 파일과 선택적인 `browserTranscript`를 백엔드에 전송한다.
3. 백엔드는 파일을 검증한 뒤 CLOVA CSR 단문 인식 API를 호출한다.
4. CLOVA가 성공하면 `CLOVA_CSR`, CLOVA가 실패하고 브라우저 문장이 있으면
   `WEB_SPEECH_FALLBACK`으로 응답한다.
5. 사용자는 반환된 문장을 확인하거나 수정한 뒤 `POST /api/v1/analyze`에 전달한다.

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

사용자가 음성 인식 결과를 확인하거나 수정한 뒤 아래처럼 호출한다. CLOVA CSR은 confidence를
제공하지 않으므로 `sttConfidence`는 `null`이다.

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

## 오류 코드

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `INVALID_AUDIO` | 파일 누락, 빈 파일, 미지원 MIME, 파일 시그니처 불일치 |
| 400 | `INVALID_INPUT` | `browserTranscript`가 1,000자를 초과 |
| 401 | `UNAUTHORIZED` | 인증 토큰 누락 또는 오류 |
| 413 | `AUDIO_TOO_LARGE` | 3MB 초과 |
| 502 | `STT_ERROR` | CLOVA 인증·할당량·서버 오류, 타임아웃, 응답 파싱 실패 |

`browserTranscript`가 제공되면 CLOVA에서 발생한 `INVALID_AUDIO`, `AUDIO_TOO_LARGE`,
`STT_ERROR` 대신 Web Speech 폴백 응답을 우선 반환한다. 백엔드 자체 파일 검증에서 거부된
요청은 폴백하지 않는다.
