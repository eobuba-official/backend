-- 어부바 백엔드 스키마 v1.2
-- 명세서 §8 데이터 모델 기준. MySQL 8.0.16+ (CHECK 제약 강제 필요)
--
-- DB 생성 시 문자셋을 DB 레벨에 지정할 것:
--   CREATE DATABASE abuba CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--
-- 운영 메모: sms_verification은 180초 만료로 행이 계속 쌓임.
-- 해커톤 범위에서는 정리 배치 없이 방치. 운영 전환 시 이벤트 스케줄러로 만료분 삭제.

CREATE TABLE `task_type` (
	`code`	VARCHAR(40)	NOT NULL	COMMENT '업무유형 코드 (예: PASSBOOK_REISSUE)',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '업무명',
	`easy_description`	VARCHAR(300)	NOT NULL	COMMENT '쉬운 말 병기 설명',
	-- task_visit_rule 행이 없을 때의 폴백 값. rule이 1:1로 항상 존재하면 사용되지 않음
	`default_visit_decision`	VARCHAR(20)	NOT NULL	COMMENT '폴백 방문판단 (rule 부재 시)',
	CONSTRAINT `PK_TASK_TYPE` PRIMARY KEY (`code`),
	CONSTRAINT `CK_TASK_TYPE_DECISION` CHECK (`default_visit_decision` IN ('NO_VISIT','CHECK_NEEDED','VISIT_REQUIRED'))
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `user` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`phone_number`	VARCHAR(11)	NOT NULL	COMMENT '휴대폰 번호 (숫자만), 계정 식별자',
	`name`	VARCHAR(50)	NOT NULL	COMMENT '사용자 이름',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT `PK_USER` PRIMARY KEY (`id`),
	CONSTRAINT `UK_USER_PHONE` UNIQUE (`phone_number`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `guardian` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL,
	`name`	VARCHAR(50)	NOT NULL	COMMENT '자녀/보호자 이름',
	-- UNIQUE 미적용은 의도: 한 자녀가 부모 두 명의 보호자로 등록될 수 있음
	`phone_number`	VARCHAR(11)	NOT NULL,
	`relation`	VARCHAR(20)	NOT NULL	COMMENT '아들 | 딸 | 배우자 | 기타',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	-- guardian_notification이 guardian.id를 FK 참조하므로 소프트 삭제 (알림 이력 보존)
	`deleted_at`	DATETIME	NULL	COMMENT '소프트 삭제 시각 (NULL = 활성)',
	CONSTRAINT `PK_GUARDIAN` PRIMARY KEY (`id`),
	CONSTRAINT `FK_user_TO_guardian` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `sms_verification` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`phone_number`	VARCHAR(11)	NOT NULL,
	`code`	VARCHAR(6)	NOT NULL	COMMENT '인증번호 (Mock 발송)',
	`purpose`	VARCHAR(20)	NOT NULL	COMMENT 'LOGIN | SIGNUP',
	`expires_at`	DATETIME	NOT NULL	COMMENT '발송 후 180초',
	`verified`	BOOLEAN	NOT NULL	DEFAULT FALSE,
	`attempt_count`	INT	NOT NULL	DEFAULT 0	COMMENT '검증 실패 횟수 (5회 초과 시 무효)',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP	COMMENT '최신 발송분 판별용',
	CONSTRAINT `PK_SMS_VERIFICATION` PRIMARY KEY (`id`),
	CONSTRAINT `CK_SMS_PURPOSE` CHECK (`purpose` IN ('LOGIN','SIGNUP')),
	INDEX `IX_SMS_PHONE_PURPOSE` (`phone_number`, `purpose`, `created_at`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `branch` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`name`	VARCHAR(100)	NOT NULL	COMMENT '지점명',
	`address`	VARCHAR(200)	NOT NULL,
	`phone`	VARCHAR(20)	NULL,
	`lat`	DECIMAL(10, 7)	NOT NULL	COMMENT '위도',
	`lng`	DECIMAL(10, 7)	NOT NULL	COMMENT '경도',
	`region_code`	VARCHAR(10)	NOT NULL	COMMENT '법정동 코드 (폴백 검색용)',
	CONSTRAINT `PK_BRANCH` PRIMARY KEY (`id`),
	INDEX `IX_BRANCH_REGION` (`region_code`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `branch_task` (
	`branch_id`	BIGINT	NOT NULL,
	`task_type_code`	VARCHAR(40)	NOT NULL,
	CONSTRAINT `PK_BRANCH_TASK` PRIMARY KEY (`branch_id`, `task_type_code`),
	CONSTRAINT `FK_branch_TO_branch_task` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`),
	CONSTRAINT `FK_task_type_TO_branch_task` FOREIGN KEY (`task_type_code`) REFERENCES `task_type` (`code`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `congestion_slot` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`branch_id`	BIGINT	NOT NULL,
	`day_of_week`	TINYINT	NOT NULL	COMMENT '1=월 ~ 7=일',
	`time_slot`	VARCHAR(20)	NOT NULL	COMMENT '예: 10:00-11:00',
	`expected_wait_minutes`	INT	NOT NULL	COMMENT '예상 대기시간(분)',
	CONSTRAINT `PK_CONGESTION_SLOT` PRIMARY KEY (`id`),
	CONSTRAINT `UK_CONGESTION_SLOT` UNIQUE (`branch_id`, `day_of_week`, `time_slot`),
	CONSTRAINT `CK_CONGESTION_DOW` CHECK (`day_of_week` BETWEEN 1 AND 7),
	CONSTRAINT `FK_branch_TO_congestion_slot` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `task_visit_rule` (
	`task_type_code`	VARCHAR(40)	NOT NULL	COMMENT '업무유형 코드 (1:1)',
	`decision`	VARCHAR(20)	NOT NULL	COMMENT 'NO_VISIT | CHECK_NEEDED | VISIT_REQUIRED',
	`reason`	VARCHAR(300)	NOT NULL	COMMENT '쉬운 말 사유',
	`remote_methods`	JSON	NULL	COMMENT 'NO_VISIT일 때 비대면 처리 방법 목록',
	`official_channels`	JSON	NULL	COMMENT 'CHECK_NEEDED일 때 공식 확인 채널 목록',
	CONSTRAINT `PK_TASK_VISIT_RULE` PRIMARY KEY (`task_type_code`),
	CONSTRAINT `CK_VISIT_RULE_DECISION` CHECK (`decision` IN ('NO_VISIT','CHECK_NEEDED','VISIT_REQUIRED')),
	CONSTRAINT `FK_task_type_TO_task_visit_rule` FOREIGN KEY (`task_type_code`) REFERENCES `task_type` (`code`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `checklist_item` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`task_type_code`	VARCHAR(40)	NOT NULL,
	`item_code`	VARCHAR(30)	NOT NULL	COMMENT 'ID_CARD | SEAL | POA | FAMILY_CERT ...',
	`name`	VARCHAR(100)	NOT NULL	COMMENT '준비물명',
	`easy_description`	VARCHAR(300)	NOT NULL	COMMENT '쉬운 말 설명',
	`required`	BOOLEAN	NOT NULL	COMMENT 'true=필수, false=조건부',
	`item_condition`	VARCHAR(300)	NULL	COMMENT '조건부일 때 조건 설명',
	`display_order`	INT	NOT NULL	DEFAULT 0,
	CONSTRAINT `PK_CHECKLIST_ITEM` PRIMARY KEY (`id`),
	CONSTRAINT `UK_CHECKLIST_ITEM` UNIQUE (`task_type_code`, `item_code`),
	CONSTRAINT `FK_task_type_TO_checklist_item` FOREIGN KEY (`task_type_code`) REFERENCES `task_type` (`code`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `consultation` (
	`id`	CHAR(36)	NOT NULL	COMMENT 'UUID, consultationId',
	`user_id`	BIGINT	NOT NULL	COMMENT 'v1.1: 로그인 필수',
	`utterance`	VARCHAR(1000)	NOT NULL	COMMENT '발화 원문 (로그·이력용, 화면 미노출)',
	`corrected_utterance`	VARCHAR(1000)	NOT NULL	COMMENT 'v1.2: STT 오인식 교정 문장. 화면 표시는 항상 이 값',
	`input_method`	VARCHAR(10)	NOT NULL	COMMENT 'VOICE | TEXT',
	`stt_confidence`	DECIMAL(3, 2)	NULL	COMMENT 'STT 인식 확신도 0~1 (로그용, 판정 미사용)',
	`status`	VARCHAR(30)	NOT NULL,
	`confidence`	DECIMAL(3, 2)	NULL	COMMENT 'LLM 분류 확신도 0~1',
	`task_type_code`	VARCHAR(40)	NULL	COMMENT '확정된 업무 (nullable)',
	`warning_dismissed_at`	DATETIME	NULL	COMMENT 'v1.2: 사기 경고 해제 시각',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT `PK_CONSULTATION` PRIMARY KEY (`id`),
	CONSTRAINT `CK_CONSULTATION_STATUS` CHECK (`status` IN ('FRAUD_WARNING','TASK_CONFIRMED','CANDIDATES_SUGGESTED','UNCLASSIFIED','WARNING_DISMISSED')),
	CONSTRAINT `CK_CONSULTATION_INPUT` CHECK (`input_method` IN ('VOICE','TEXT')),
	CONSTRAINT `FK_user_TO_consultation` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
	CONSTRAINT `FK_task_type_TO_consultation` FOREIGN KEY (`task_type_code`) REFERENCES `task_type` (`code`),
	INDEX `IX_CONSULTATION_USER` (`user_id`, `created_at`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 후보 업무 목록.
-- 주의: 사기 감지(FRAUD_WARNING)로 CANDIDATES 분류가 보류된 경우에도 후보 행은 여기 저장된다.
-- FRAUD_WARNING 상태의 /analyze 응답에는 candidates를 노출하지 않고,
-- dismiss-warning 시 이 행들을 읽어 복원한다 (응답 조립 로직에서 status로 분기).
CREATE TABLE `consultation_candidate` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`consultation_id`	CHAR(36)	NOT NULL,
	`task_type_code`	VARCHAR(40)	NOT NULL,
	`display_order`	INT	NOT NULL	COMMENT '1~3, 버튼 표시 순서',
	CONSTRAINT `PK_CONSULTATION_CANDIDATE` PRIMARY KEY (`id`),
	CONSTRAINT `UK_CANDIDATE_TASK` UNIQUE (`consultation_id`, `task_type_code`),
	CONSTRAINT `UK_CANDIDATE_ORDER` UNIQUE (`consultation_id`, `display_order`),
	CONSTRAINT `FK_consultation_TO_candidate` FOREIGN KEY (`consultation_id`) REFERENCES `consultation` (`id`),
	CONSTRAINT `FK_task_type_TO_candidate` FOREIGN KEY (`task_type_code`) REFERENCES `task_type` (`code`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- v1.2 신규: 사기 감지로 보류된 분류 결과. dismiss-warning 시 반환.
-- classification_status가 CANDIDATES면 후보 목록은 consultation_candidate에서 조회
CREATE TABLE `consultation_result` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`consultation_id`	CHAR(36)	NOT NULL,
	`task_type_code`	VARCHAR(40)	NULL	COMMENT '보류된 확정 업무 (CANDIDATES/UNCLASSIFIED 보류 시 NULL)',
	`confidence`	DECIMAL(3, 2)	NULL	COMMENT '보류 시점 LLM 확신도',
	`classification_status`	VARCHAR(20)	NOT NULL	COMMENT '보류된 분류 상태',
	CONSTRAINT `PK_CONSULTATION_RESULT` PRIMARY KEY (`id`),
	CONSTRAINT `UK_CONSULTATION_RESULT` UNIQUE (`consultation_id`),
	CONSTRAINT `CK_RESULT_STATUS` CHECK (`classification_status` IN ('CONFIRMED','CANDIDATES','UNCLASSIFIED')),
	CONSTRAINT `FK_consultation_TO_result` FOREIGN KEY (`consultation_id`) REFERENCES `consultation` (`id`),
	CONSTRAINT `FK_task_type_TO_result` FOREIGN KEY (`task_type_code`) REFERENCES `task_type` (`code`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `fraud_detection` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`consultation_id`	CHAR(36)	NOT NULL,
	`pattern_type`	VARCHAR(20)	NOT NULL,
	`evidence`	VARCHAR(500)	NOT NULL	COMMENT '감지된 발화 문구',
	`explanation`	VARCHAR(500)	NOT NULL	COMMENT '쉬운 말 근거 설명',
	CONSTRAINT `PK_FRAUD_DETECTION` PRIMARY KEY (`id`),
	CONSTRAINT `CK_FRAUD_PATTERN` CHECK (`pattern_type` IN ('IMPERSONATION','SAFE_ACCOUNT','SECRECY','REMOTE_CONTROL','URGENCY')),
	CONSTRAINT `FK_consultation_TO_fraud` FOREIGN KEY (`consultation_id`) REFERENCES `consultation` (`id`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `guardian_notification` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`consultation_id`	CHAR(36)	NOT NULL,
	`guardian_id`	BIGINT	NOT NULL,
	`message`	VARCHAR(300)	NOT NULL	COMMENT 'Mock SMS 내용',
	`sent_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT `PK_GUARDIAN_NOTIFICATION` PRIMARY KEY (`id`),
	CONSTRAINT `FK_consultation_TO_notification` FOREIGN KEY (`consultation_id`) REFERENCES `consultation` (`id`),
	CONSTRAINT `FK_guardian_TO_notification` FOREIGN KEY (`guardian_id`) REFERENCES `guardian` (`id`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE `recommendation` (
	`id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`consultation_id`	CHAR(36)	NOT NULL,
	`branch_id`	BIGINT	NOT NULL,
	`display_rank`	INT	NOT NULL	COMMENT '1~3 (rank는 MySQL 8 예약어라 개명)',
	`visit_date`	DATE	NOT NULL,
	`time_slot`	VARCHAR(20)	NOT NULL	COMMENT '예: 10:00-11:00',
	`expected_wait_minutes`	INT	NOT NULL,
	`score`	DECIMAL(5, 1)	NOT NULL	COMMENT '가중 점수 0~100',
	`sentence`	VARCHAR(300)	NOT NULL	COMMENT '자연어 추천 문장',
	`created_at`	DATETIME	NOT NULL	DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT `PK_RECOMMENDATION` PRIMARY KEY (`id`),
	CONSTRAINT `UK_RECOMMENDATION_RANK` UNIQUE (`consultation_id`, `display_rank`),
	CONSTRAINT `FK_consultation_TO_recommendation` FOREIGN KEY (`consultation_id`) REFERENCES `consultation` (`id`),
	CONSTRAINT `FK_branch_TO_recommendation` FOREIGN KEY (`branch_id`) REFERENCES `branch` (`id`)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
