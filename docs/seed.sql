-- 어부바 Mock 시드 데이터 v1.2
-- docs/schema.sql 적용 후 실행. 재실행 가능하도록 역순 삭제 후 삽입.
--
-- 혼잡도 패턴 (팀 확정, 2026-09-03):
--   오전(09~11시) 한가 / 점심·오후(12~16시) 혼잡 — 시연 시 "내일 오전 추천" 문장이 성립하도록 설계.
--   congestion_slot에 데이터가 없는 조합은 기본 대기 15분(application.yml abuba.recommendation.default-wait-minutes).

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM `recommendation`;
DELETE FROM `guardian_notification`;
DELETE FROM `fraud_detection`;
DELETE FROM `consultation_result`;
DELETE FROM `consultation_candidate`;
DELETE FROM `consultation`;
DELETE FROM `congestion_slot`;
DELETE FROM `branch_task`;
DELETE FROM `checklist_item`;
DELETE FROM `task_visit_rule`;
DELETE FROM `branch`;
DELETE FROM `task_type`;
SET FOREIGN_KEY_CHECKS = 1;

-- ── 업무유형 8종 (명세 §7) ─────────────────────────────────────────────
INSERT INTO `task_type` (`code`, `name`, `easy_description`, `default_visit_decision`) VALUES
('PASSBOOK_REISSUE',    '통장 재발급',        '통장을 잃어버렸을 때 새로 만드는 일',      'VISIT_REQUIRED'),
('PROXY_TASK',          '대리 업무',          '가족 일을 대신 처리하는 것',               'VISIT_REQUIRED'),
('DEPOSIT_EARLY_CLOSE', '예금 중도해지',      '만기 전에 돈을 찾는 것',                   'CHECK_NEEDED'),
('CARD_REISSUE',        '카드 재발급',        '카드를 새로 받는 일',                      'CHECK_NEEDED'),
('PASSWORD_CHANGE',     '비밀번호 변경',      '비밀번호를 바꾸거나 찾는 일',              'CHECK_NEEDED'),
('AUTO_TRANSFER_CHANGE','자동이체 변경',      '매달 자동으로 나가는 돈을 바꾸는 것',      'NO_VISIT'),
('BALANCE_INQUIRY',     '잔액·거래내역 조회', '통장에 얼마 있는지 보는 일',               'NO_VISIT'),
('ACCOUNT_TRANSFER',    '계좌이체',           '다른 사람에게 돈을 보내는 일',             'NO_VISIT');

-- ── 방문 판단 룰 (룰 엔진, 명세 §3·§4 응답 예시 기반) ──────────────────
INSERT INTO `task_visit_rule` (`task_type_code`, `decision`, `reason`, `remote_methods`, `official_channels`) VALUES
('PASSBOOK_REISSUE', 'VISIT_REQUIRED',
 '통장 재발급은 본인 확인이 필요해 지점 방문이 필요합니다.', NULL, NULL),
('PROXY_TASK', 'VISIT_REQUIRED',
 '가족 일을 대신 처리하려면 서류 확인이 필요해 지점 방문이 필요합니다.', NULL, NULL),
('DEPOSIT_EARLY_CLOSE', 'CHECK_NEEDED',
 '상품에 따라 앱에서 해지가 가능할 수 있어요. 먼저 확인해 보세요.', NULL,
 '[{"name":"KB국민은행 고객센터","phone":"1588-9999","description":"해지 가능 여부를 전화로 확인"}]'),
('CARD_REISSUE', 'CHECK_NEEDED',
 '카드 종류에 따라 앱이나 전화로 재발급이 가능할 수 있어요. 먼저 확인해 보세요.', NULL,
 '[{"name":"KB국민카드 고객센터","phone":"1588-1688","description":"재발급 방법을 전화로 확인"}]'),
('PASSWORD_CHANGE', 'CHECK_NEEDED',
 '비밀번호 종류에 따라 앱에서 바꿀 수 있어요. 먼저 확인해 보세요.', NULL,
 '[{"name":"KB국민은행 고객센터","phone":"1588-9999","description":"변경 가능한 방법을 전화로 확인"}]'),
('AUTO_TRANSFER_CHANGE', 'NO_VISIT',
 '자동이체 변경은 앱이나 전화로 하실 수 있어요.',
 '[{"channel":"MOBILE_APP","description":"KB스타뱅킹 자동이체 관리","easyDescription":"휴대폰 앱으로 바꾸기"},{"channel":"CALL_CENTER","description":"고객센터 1588-9999","easyDescription":"전화로 바꾸기"}]', NULL),
('BALANCE_INQUIRY', 'NO_VISIT',
 '잔액과 거래내역은 앱이나 ATM에서 보실 수 있어요.',
 '[{"channel":"MOBILE_APP","description":"KB스타뱅킹 조회","easyDescription":"휴대폰 앱으로 보기"},{"channel":"ATM","description":"ATM 조회","easyDescription":"은행 기계로 보기"}]', NULL),
('ACCOUNT_TRANSFER', 'NO_VISIT',
 '계좌이체는 앱이나 ATM으로 하실 수 있어요.',
 '[{"channel":"MOBILE_APP","description":"KB스타뱅킹 이체","easyDescription":"휴대폰 앱으로 보내기"},{"channel":"ATM","description":"ATM 계좌이체","easyDescription":"은행 기계로 보내기"}]', NULL);

-- ── 준비물 체크리스트 (방문 필요/확인 필요 업무 위주) ──────────────────
INSERT INTO `checklist_item` (`task_type_code`, `item_code`, `name`, `easy_description`, `required`, `item_condition`, `display_order`) VALUES
-- 통장 재발급 (명세 §5 예시 그대로)
('PASSBOOK_REISSUE', 'ID_CARD',     '신분증',           '주민등록증이나 운전면허증',                TRUE,  NULL, 1),
('PASSBOOK_REISSUE', 'SEAL',        '도장',             '통장 만들 때 쓴 도장',                     FALSE, '서명으로 만든 통장이면 필요 없어요', 2),
('PASSBOOK_REISSUE', 'POA',         '위임장',           '다른 사람이 대신 갈 때 필요한 종이',       FALSE, '가족이 대신 방문하는 경우', 3),
('PASSBOOK_REISSUE', 'FAMILY_CERT', '가족관계증명서',   '가족임을 증명하는 종이',                   FALSE, '가족이 대신 방문하는 경우', 4),
-- 대리 업무
('PROXY_TASK', 'ID_CARD',       '방문자 신분증',    '지점에 가는 분의 주민등록증이나 운전면허증', TRUE,  NULL, 1),
('PROXY_TASK', 'OWNER_ID_CARD', '본인 신분증',      '업무 당사자의 신분증 (사본 가능 여부는 지점 확인)', TRUE, NULL, 2),
('PROXY_TASK', 'POA',           '위임장',           '일을 맡긴다는 내용을 적은 종이',            TRUE,  NULL, 3),
('PROXY_TASK', 'FAMILY_CERT',   '가족관계증명서',   '가족임을 증명하는 종이',                    TRUE,  NULL, 4),
-- 예금 중도해지
('DEPOSIT_EARLY_CLOSE', 'ID_CARD',  '신분증',       '주민등록증이나 운전면허증',                TRUE,  NULL, 1),
('DEPOSIT_EARLY_CLOSE', 'PASSBOOK', '통장',         '해지할 예금 통장',                         FALSE, '통장 없이 만든 예금이면 필요 없어요', 2),
('DEPOSIT_EARLY_CLOSE', 'SEAL',     '도장',         '예금 만들 때 쓴 도장',                     FALSE, '서명으로 만든 예금이면 필요 없어요', 3),
-- 카드 재발급
('CARD_REISSUE', 'ID_CARD', '신분증', '주민등록증이나 운전면허증', TRUE, NULL, 1),
-- 비밀번호 변경
('PASSWORD_CHANGE', 'ID_CARD',  '신분증', '주민등록증이나 운전면허증', TRUE,  NULL, 1),
('PASSWORD_CHANGE', 'PASSBOOK', '통장',   '비밀번호를 바꿀 통장',      FALSE, '통장 비밀번호를 바꾸는 경우', 2);

-- ── 지점 (서울 종로 일대, 명세 §6 예시 기반) ──────────────────────────
INSERT INTO `branch` (`id`, `name`, `address`, `phone`, `lat`, `lng`, `region_code`) VALUES
(87,  'KB국민은행 광화문지점', '서울 종로구 세종대로 2',  '02-000-0001', 37.5716000, 126.9767000, '1111011200'),
(103, 'KB국민은행 종로지점',   '서울 종로구 종로 1',      '02-000-0000', 37.5700000, 126.9820000, '1111013500'),
(120, 'KB국민은행 을지로지점', '서울 중구 을지로 3',      '02-000-0002', 37.5660000, 126.9910000, '1114012000');

-- 세 지점 모두 8개 업무 전부 처리 가능 (시연 단순화)
INSERT INTO `branch_task` (`branch_id`, `task_type_code`)
SELECT b.id, t.code FROM `branch` b CROSS JOIN `task_type` t;

-- ── 혼잡도: 평일(월~금) × 7개 시간대 × 3개 지점 ───────────────────────
-- 기본 패턴: 오전 한가(5~10분), 점심 피크(25~35분), 오후 혼잡(20~30분)
-- 지점별 오프셋: 광화문(87) +4분, 종로(103) +0분, 을지로(120) +7분 → 동점 방지·종로가 1순위 되도록
INSERT INTO `congestion_slot` (`branch_id`, `day_of_week`, `time_slot`, `expected_wait_minutes`)
SELECT b.id,
       d.dow,
       s.slot,
       s.base_wait + b.wait_offset + IF(d.dow = 1, 5, 0)  -- 월요일은 전반적으로 +5분
FROM (SELECT 87 AS id, 4 AS wait_offset
      UNION ALL SELECT 103, 0
      UNION ALL SELECT 120, 7) b
CROSS JOIN (SELECT 1 AS dow UNION ALL SELECT 2 UNION ALL SELECT 3
            UNION ALL SELECT 4 UNION ALL SELECT 5) d
CROSS JOIN (SELECT '09:00-10:00' AS slot,  8 AS base_wait
            UNION ALL SELECT '10:00-11:00',  5
            UNION ALL SELECT '11:00-12:00', 12
            UNION ALL SELECT '12:00-13:00', 30
            UNION ALL SELECT '13:00-14:00', 25
            UNION ALL SELECT '14:00-15:00', 18
            UNION ALL SELECT '15:00-16:00', 22) s;
