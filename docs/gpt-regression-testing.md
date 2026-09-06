# 실제 GPT 업무 분류 회귀 테스트

실제 GPT 회귀 테스트는 일반 단위 테스트와 분리된 opt-in 검증입니다. `bash gradlew test`에서는 외부 API를 호출하지 않으며, 아래 전용 태스크를 실행할 때만 고정 평가 데이터셋을 프록시에 전송합니다.

```bash
bash gradlew gptRegressionTest
```

## 실행 설정

설정 우선순위는 환경변수, Git에서 제외된 `application-local.properties`, 회귀 테스트 기본 프로필 순서입니다.

| 환경변수 | 필수 | 설명 |
|---|---|---|
| `GPT_REGRESSION_API_KEY` | 조건부 | 프록시 또는 OpenAI API Key. 없으면 local profile의 LLM Key를 사용합니다. |
| `GPT_REGRESSION_BASE_URL` | 조건부 | OpenAI Chat Completions 호환 base URL. 없으면 local profile 값을 사용합니다. |
| `GPT_REGRESSION_MODEL` | 선택 | 평가 모델. 기본값은 `gpt-5-nano`입니다. |

API Key나 base URL을 어느 경로에서도 찾지 못하면 테스트는 실패하지 않고, 누락된 설정명을 이유로 표시하며 건너뜁니다. Key, Authorization 헤더, 평가 발화는 테스트 로그와 리포트에 기록하지 않습니다.

`gpt-5-nano`는 Chat Completions와 Structured Outputs를 지원합니다. 모델 지원 범위는 [OpenAI Docs의 GPT-5 nano 모델 문서](https://developers.openai.com/api/docs/models/gpt-5-nano)를 기준으로 확인합니다.

## 평가 데이터셋

고정 데이터셋은 `src/gptRegressionTest/resources/gpt-regression-dataset.json`에서 관리합니다.

- 은행 업무 코드 8종의 명확한 발화
- `CONFIRMED`, `CANDIDATES`, `UNCLASSIFIED` confidence 흐름
- STT 오인식 교정과 `VOICE` 재확인
- 사기 패턴 5종의 단독 발화
- 5종 사기 패턴 복합 발화
- 정상적인 가족 송금과 보이스피싱 송금 대조군

각 실행은 최대 20개 요청, 단일 스레드, 케이스당 1회 호출로 제한합니다. 회귀 테스트에서는 fallback 모델을 기본 모델과 동일하게 설정해 장애 시 추가 요청을 보내지 않습니다. 연결 제한은 3초, 응답 제한은 20초입니다.

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
build/reports/gpt-regression/gpt-regression-YYYYMMDD-HHMMSS.json
build/reports/gpt-regression/latest.json
```

리포트에는 데이터셋 버전, 설정 출처, 요청·응답 모델, 프롬프트 버전, 케이스별 상태·업무·confidence, confidence 구간 분포와 성공 여부가 포함됩니다.

프롬프트 변경 전 `latest.json`을 별도 경로에 보관한 다음 변경 후 리포트와 비교합니다.

```bash
cp build/reports/gpt-regression/latest.json /tmp/gpt-regression-baseline.json
bash gradlew gptRegressionTest
diff -u /tmp/gpt-regression-baseline.json build/reports/gpt-regression/latest.json
```

시간값까지 제외한 구조 비교가 필요하면 `jq`로 `startedAt`, `completedAt`을 제거한 뒤 비교합니다.

## Swagger 수동 검증 순서

1. 로컬 서버를 실행하고 `/swagger-ui.html`을 엽니다.
2. 인증 API로 access token을 발급받습니다.
3. Swagger의 `Authorize` 버튼에 access token만 입력합니다.
4. 음성 파일을 시험하려면 `POST /api/v1/speech/transcriptions`에 지원 파일과 선택적인 `browserTranscript`를 보냅니다.
5. 반환된 `transcript`를 확인한 뒤 `POST /api/v1/analyze`에 `inputMethod: VOICE`로 전달합니다.
6. `CANDIDATES_SUGGESTED`라면 반환된 상담 ID와 후보 코드로 `POST /api/v1/consultations/{consultationId}/task-selection`을 호출합니다.
7. 사기 발화는 `FRAUD_WARNING`이 먼저 반환되고 업무·후보·VisitDecision이 숨겨지는지 확인합니다.
