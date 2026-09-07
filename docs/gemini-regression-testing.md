# 실제 Gemini 업무 분류 회귀 테스트

실제 Gemini 회귀 테스트는 일반 단위 테스트와 분리된 opt-in 검증입니다. `bash gradlew test`에서는 외부 API를 호출하지 않으며, 아래 전용 태스크를 실행할 때만 고정 평가 데이터셋을 Gemini API에 전송합니다.

```bash
bash gradlew geminiRegressionTest
```

## 실행 설정

설정 우선순위는 회귀 테스트 전용 환경변수, `GEMINI_API_KEY`, Git에서 제외된 `application-local.properties`, 회귀 테스트 기본 프로필 순서입니다.

| 환경변수 | 필수 | 설명 |
|---|---|---|
| `GEMINI_REGRESSION_API_KEY` | 조건부 | 회귀 테스트 전용 Gemini API Key입니다. |
| `GEMINI_API_KEY` | 조건부 | 애플리케이션과 공유하는 Gemini API Key입니다. 전용 Key가 없을 때 사용합니다. |
| `GEMINI_REGRESSION_BASE_URL` | 선택 | Gemini API base URL입니다. 기본값은 Google Generative Language API v1beta입니다. |
| `GEMINI_REGRESSION_MODEL` | 선택 | 평가 모델입니다. 기본값은 `gemini-3.5-flash-lite`입니다. |

어느 경로에서도 API Key를 찾지 못하면 테스트는 실패하지 않고 누락된 설정명을 이유로 표시하며 건너뜁니다. Key, 인증 헤더, 평가 발화는 테스트 로그와 리포트에 기록하지 않습니다.

`gemini-3.5-flash-lite`는 JSON Schema 기반 Structured Output을 지원합니다. 모델과 기능 지원 여부는 [Gemini 3.5 Flash-Lite 공식 문서](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite)를 기준으로 확인합니다.

## 호출 제한

애플리케이션 안전 상한은 15 RPM, 500 RPD입니다. Gemini의 할당량은 API Key가 아닌 Google Cloud 프로젝트 단위로 계산되고, 실제 한도는 Google AI Studio에 표시된 값이 기준입니다. RPD는 태평양 시간 자정에 초기화됩니다.

회귀 테스트는 단일 스레드로 실행하며 케이스 사이에 4초 간격을 두어 15 RPM을 넘지 않습니다. 데이터셋은 최대 20회 호출로 제한되고, 자동 재시도와 폴백 호출은 사용하지 않습니다. 테스트를 실행하기 전에 당일 다른 애플리케이션 인스턴스가 사용한 호출량도 확인해야 합니다.

## 평가 데이터셋

고정 데이터셋은 `src/geminiRegressionTest/resources/gemini-regression-dataset.json`에서 관리합니다.

- 은행 업무 코드 8종의 명확한 발화
- `CONFIRMED`, `CANDIDATES`, `UNCLASSIFIED` confidence 흐름
- STT 오인식 교정과 `VOICE` 재확인
- 사기 패턴 5종의 단독 발화
- 5종 사기 패턴 복합 발화
- 정상적인 가족 송금과 보이스피싱 송금 대조군

## 검증 항목

- Structured Output 필수 값과 confidence 0~1 범위
- 허용된 8종 intent와 candidate만 노출되는지 여부
- 후보 중복 제거와 `CANDIDATES` 결과의 2~3개 제한
- 기대 업무 코드와 분류 상태
- STT 교정 결과와 음성 재확인 여부
- 사기 패턴 코드, 원문 evidence, explanation
- 사기 패턴 단독·복합 탐지와 정상 송금 false positive
- 실제 응답 모델명과 프롬프트 버전

## 결과 리포트와 프롬프트 비교

실행 결과는 발화 원문을 제외한 JSON으로 생성됩니다.

```text
build/reports/gemini-regression/gemini-regression-YYYYMMDD-HHMMSS.json
build/reports/gemini-regression/latest.json
```

리포트에는 데이터셋 버전, 설정 출처, 요청·응답 모델, 프롬프트 버전, 케이스별 상태·업무·confidence, confidence 구간 분포와 성공 여부가 포함됩니다.

```bash
cp build/reports/gemini-regression/latest.json /tmp/gemini-regression-baseline.json
bash gradlew geminiRegressionTest
diff -u /tmp/gemini-regression-baseline.json build/reports/gemini-regression/latest.json
```

## Swagger 수동 검증 순서

1. `GEMINI_API_KEY`를 설정하고 로컬 서버를 실행한 뒤 `/swagger-ui.html`을 엽니다.
2. 인증 API로 access token을 발급받고 Swagger의 `Authorize`에 입력합니다.
3. 음성 파일을 시험하려면 `POST /api/v1/speech/transcriptions`에 지원 파일과 선택적인 `browserTranscript`를 보냅니다.
4. 반환된 `transcript`를 확인한 뒤 `POST /api/v1/analyze`에 `inputMethod: VOICE`로 전달합니다.
5. `CANDIDATES_SUGGESTED`라면 상담 ID와 후보 코드로 `POST /api/v1/consultations/{consultationId}/task-selection`을 호출합니다.
6. 사기 발화는 `FRAUD_WARNING`이 먼저 반환되고 업무·후보·VisitDecision이 숨겨지는지 확인합니다.
