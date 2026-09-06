# 업무 분류 데모 시나리오

| 발화 | 기대 교정/분류 | 기대 상태 |
|---|---|---|
| 통장을 잃어버려서 다시 만들고 싶어 | `PASSBOOK_REISSUE` | `CONFIRMED` |
| 자동인체 빠져나가는 날짜를 바꾸고 싶어 | `자동이체`로 교정, `AUTO_TRANSFER_CHANGE` | `CONFIRMED` |
| 아들 이름으로 은행 일을 대신해야 해 | `PROXY_TASK` 중심 후보 2~3개 | `CANDIDATES` |
| 비밀번호를 잊어서 다시 정하고 싶어 | `PASSWORD_CHANGE` | `CONFIRMED` |
| 잔액이랑 최근에 쓴 돈을 보고 싶어 | `BALANCE_INQUIRY` | `CONFIRMED` |
| 그거 있잖아 그거 좀 해줘 | 유효 intent 없음 | `UNCLASSIFIED` |
| 은행에서 안전한 계좌로 지금 당장 돈을 보내래 | `SAFE_ACCOUNT`, `URGENCY` 감지 후 분류 숨김 | `FRAUD_WARNING` |
| 검찰이라면서 아무에게도 말하지 말고 원격 앱을 설치하래 | `IMPERSONATION`, `SECRECY`, `REMOTE_CONTROL` 감지 후 분류 숨김 | `FRAUD_WARNING` |

## 확인 포인트

- LLM이 반환한 intent와 candidates는 확정 업무 코드 8종만 프론트 계약에 노출한다.
- confidence `0.75` 이상은 확정, `0.35` 이상 `0.75` 미만은 유효 후보 2~3개, 그 외는 분류 불가다.
- 음성 입력은 confidence와 관계없이 인식 문장 확인 단계를 거친다.
- 위험 패턴의 evidence는 입력 발화에 실제로 포함된 문구만 노출한다.
- 사기 여부는 LLM의 `fraud_detected` 값이 아니라 검증을 통과한 패턴이 1개 이상인지로 결정한다.
- 허용되지 않은 패턴, 빈 근거, 발화에 없는 근거와 동일한 패턴·근거 중복은 제거한다.
- 유효 패턴이 있으면 업무·후보·VisitDecision을 숨기고 `FRAUD_WARNING`을 우선 반환한다.
- 분류 결과와 후보는 보류 상태로 저장하고, 검증된 패턴과 근거는 `fraud_detection`에 저장한다.
