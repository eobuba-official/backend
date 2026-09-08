# 로컬 데이터베이스 설정

## 실행 조건

로컬 실행 시 `local` 프로필을 활성화하고, Git에서 제외된
`src/main/resources/application-local.properties`에 본인의 MySQL 접속 정보를 설정한다.

```properties
spring.datasource.username=본인_MYSQL_사용자
spring.datasource.password=본인_MYSQL_비밀번호
```

IntelliJ 실행 구성에서는 Active profiles에 `local`을 지정한다. 터미널에서는 다음과 같이 실행한다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## 필수 기준 데이터 자동 초기화

`local` 프로필로 서버를 실행하면 다음 기준 데이터 중 누락된 행만 자동으로 추가한다.

- `task_type`: 확정 업무 코드 8종
- `task_visit_rule`: 업무별 방문 판단 규칙 8종
- `checklist_item`: 준비물 기준 항목 14종

이미 존재하는 행은 수정하거나 삭제하지 않는다. 사용자, 상담, 사기 탐지, 추천 데이터에도
영향을 주지 않는다. 실행 로그의 아래 항목에서 실제 추가된 행 수를 확인할 수 있다.

```text
Local reference data initialized: taskTypes=0, visitRules=0, checklistItems=0
```

모두 `0`이면 필수 기준 데이터가 이미 준비된 상태다. 이 초기화기는 `local` 프로필에서만
동작하므로 운영 프로필에서는 실행되지 않는다.

## 전체 데모 데이터

지점, 혼잡도 등 전체 시연 데이터가 필요할 때만 `docs/seed.sql`을 수동으로 실행한다.
이 파일은 재실행 가능한 데모 상태를 만들기 위해 상담과 추천을 포함한 여러 테이블을 먼저
삭제한다. 보존해야 할 로컬 데이터가 있다면 실행하지 않는다.
