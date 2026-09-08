// KB국민은행 전국 지점 수집 스크립트 (카카오 로컬 API)
// 사용법: KAKAO_REST_KEY=<key> node scripts/collect-kb-branches.mjs
// 출력: docs/seed-branches-full.sql, docs/kb-branches.json

const KEY = process.env.KAKAO_REST_KEY;
if (!KEY) {
  console.error("KAKAO_REST_KEY 환경변수가 필요합니다.");
  process.exit(1);
}

const HEADERS = { Authorization: `KakaoAK ${KEY}` };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function kakao(path, params) {
  const url = `https://dapi.kakao.com/v2/local/${path}?${new URLSearchParams(params)}`;
  for (let attempt = 0; attempt < 5; attempt++) {
    const res = await fetch(url, { headers: HEADERS });
    if (res.status === 429) {
      await sleep(1000 * (attempt + 1));
      continue;
    }
    if (!res.ok) throw new Error(`${res.status} ${await res.text()}`);
    return res.json();
  }
  throw new Error("rate limit retry exceeded: " + url);
}

// 사각 영역 내 검색 (최대 45건). 45건 초과로 잘리면 4분할 재귀.
const found = new Map(); // kakao place id -> place
let apiCalls = 0;

async function searchRect(minLng, minLat, maxLng, maxLat, depth = 0) {
  const rect = `${minLng},${minLat},${maxLng},${maxLat}`;
  let totalCount = 0;
  for (let page = 1; page <= 3; page++) {
    const data = await kakao("search/keyword.json", {
      query: "KB국민은행",
      category_group_code: "BK9",
      rect,
      size: 15,
      page,
    });
    apiCalls++;
    await sleep(25);
    totalCount = data.meta.total_count;
    if (page === 1 && totalCount > 45 && depth < 12) {
      // 분할 재귀
      const midLng = (minLng + maxLng) / 2;
      const midLat = (minLat + maxLat) / 2;
      await searchRect(minLng, minLat, midLng, midLat, depth + 1);
      await searchRect(midLng, minLat, maxLng, midLat, depth + 1);
      await searchRect(minLng, midLat, midLng, maxLat, depth + 1);
      await searchRect(midLng, midLat, maxLng, maxLat, depth + 1);
      return;
    }
    for (const d of data.documents) found.set(d.id, d);
    if (data.meta.is_end) break;
  }
}

function isBranch(p) {
  // BK9 + KB국민은행 카테고리, ATM/365코너 제외
  if (!p.category_name.includes("KB국민은행")) return false;
  if (/ATM|365|자동화|무인/i.test(p.place_name)) return false;
  return true;
}

async function regionCode(x, y) {
  const data = await kakao("geo/coord2regioncode.json", { x, y });
  apiCalls++;
  await sleep(25);
  const b = data.documents.find((d) => d.region_type === "B");
  return b ? b.code : null;
}

const esc = (s) => String(s ?? "").replace(/\\/g, "\\\\").replace(/'/g, "''");

async function main() {
  console.log("1/3 전국 격자 검색 시작...");
  // 남한 대략 경계 (제주 포함)
  await searchRect(124.6, 33.0, 131.0, 38.7);
  console.log(`  수집 완료: ${found.size}개 장소, API ${apiCalls}회`);

  const branches = [...found.values()].filter(isBranch);
  console.log(`2/3 지점 필터 후 ${branches.length}개 — 법정동코드 변환 중...`);

  const rows = [];
  let id = 1;
  for (const p of branches) {
    const code = await regionCode(p.x, p.y);
    if (!code) continue;
    rows.push({
      id: id++,
      name: p.place_name,
      address: p.road_address_name || p.address_name,
      phone: p.phone || null,
      lat: Number(p.y).toFixed(7),
      lng: Number(p.x).toFixed(7),
      regionCode: code,
    });
    if (rows.length % 100 === 0) console.log(`  ${rows.length}/${branches.length}`);
  }

  console.log("3/3 SQL 생성...");
  const values = rows
    .map(
      (r) =>
        `(${r.id}, '${esc(r.name)}', '${esc(r.address)}', ${r.phone ? `'${esc(r.phone)}'` : "NULL"}, ${r.lat}, ${r.lng}, '${r.regionCode}')`
    )
    .join(",\n");

  const sql = `-- KB국민은행 전국 지점 seed (카카오 로컬 API 수집, 생성일 ${new Date().toISOString().slice(0, 10)})
-- 생성: node scripts/collect-kb-branches.mjs
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM \`congestion_slot\`;
DELETE FROM \`branch_task\`;
DELETE FROM \`branch\`;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO \`branch\` (\`id\`, \`name\`, \`address\`, \`phone\`, \`lat\`, \`lng\`, \`region_code\`) VALUES
${values};

-- 전 지점 × 8개 업무 전부 처리 가능 (시연 단순화)
INSERT INTO \`branch_task\` (\`branch_id\`, \`task_type_code\`)
SELECT b.id, t.code FROM \`branch\` b CROSS JOIN \`task_type\` t;

-- 혼잡도 mock: 평일 5일 × 7개 시간대, 지점별 오프셋 = id % 11 (0~10분), 월요일 +5분
INSERT INTO \`congestion_slot\` (\`branch_id\`, \`day_of_week\`, \`time_slot\`, \`expected_wait_minutes\`)
SELECT b.id, d.dow, s.slot, s.base_wait + (b.id % 11) + IF(d.dow = 1, 5, 0)
FROM \`branch\` b
CROSS JOIN (SELECT 1 AS dow UNION ALL SELECT 2 UNION ALL SELECT 3
            UNION ALL SELECT 4 UNION ALL SELECT 5) d
CROSS JOIN (SELECT '09:00-10:00' AS slot,  8 AS base_wait
            UNION ALL SELECT '10:00-11:00',  5
            UNION ALL SELECT '11:00-12:00', 12
            UNION ALL SELECT '12:00-13:00', 30
            UNION ALL SELECT '13:00-14:00', 25
            UNION ALL SELECT '14:00-15:00', 18
            UNION ALL SELECT '15:00-16:00', 22) s;
`;

  const fs = await import("node:fs");
  fs.writeFileSync(new URL("../docs/seed-branches-full.sql", import.meta.url), sql, "utf8");
  fs.writeFileSync(
    new URL("../docs/kb-branches.json", import.meta.url),
    JSON.stringify(rows, null, 2),
    "utf8"
  );
  console.log(`완료: ${rows.length}개 지점 → docs/seed-branches-full.sql (API 총 ${apiCalls}회)`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
