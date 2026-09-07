# EC2 자동 배포 가이드

`dev` 브랜치에 push/머지되면 GitHub Actions가 Docker 이미지를 빌드해 EC2에 자동 배포합니다.
관련 이슈: #30 · 파이프라인 정의: `.github/workflows/deploy.yml`

```
dev push → Actions: test + bootJar → GHCR 이미지 push (latest + sha)
        → deploy/ 설정 scp 동기화 → EC2에서 compose pull & up → /api/health 게이트
```

## 구성

| 구성요소 | 내용 |
|---|---|
| EC2 | 프리티어 1대 (t2.micro/t3.micro, Amazon Linux 2023, 서울 리전) + Elastic IP |
| 컨테이너 | app (Spring Boot) + mysql:8.0 + nginx — `deploy/docker-compose.prod.yml` |
| 레지스트리 | GHCR `ghcr.io/eobuba-official/backend` (public 권장) |
| DB 초기화 | `docs/schema.sql` + `docs/seed.sql` → 빈 볼륨 최초 1회 initdb 적재 |
| 시크릿 | EC2의 `/opt/piggyback/.env` + GitHub Secrets (repo에는 없음) |

## 최초 1회 셋업 (사용자 작업)

### 1. AWS 계정·비용 확인
- 계정 생성 시점이 **2025-07-15 이후**면 750h 프리티어가 아니라 크레딧 기반 무료 플랜 — 콘솔의 Free Tier 페이지에서 확인
- Public IPv4는 2024-02부터 과금 대상 (프리티어/크레딧 범위 확인)

### 2. EC2 생성 + Elastic IP
- 서울 리전, Amazon Linux 2023, t2.micro 또는 t3.micro (x86)
- **Elastic IP 할당·연결** (없으면 stop/start 시 IP가 바뀌어 배포·URL 동시 파손)
- 보안그룹 인바운드(최초): **80 (0.0.0.0/0)** + **22 (내 IP만)**

### 3. 서버 프로비저닝 (부트스트랩 순서 중요)
1. 22번으로 SSH 접속 → `sudo bash setup-ec2.sh` 실행 (`deploy/setup-ec2.sh` 업로드 후)
   - Docker + compose plugin, 스왑 2GB(영속), deploy 유저, sshd 2222 이동까지 자동
2. **기존 세션을 열어둔 채** 새 터미널에서 `ssh -p 2222 deploy@<IP>` 확인
3. 성공하면 보안그룹에서 **2222 (0.0.0.0/0) 추가, 22 규칙 제거**
   - 2222는 봇 노이즈 감소용일 뿐, 보안 경계는 SSH 키

### 4. 시크릿 작성
EC2에서 (deploy 유저로):
```bash
cat > /opt/piggyback/.env <<'EOF'
MYSQL_PASSWORD=<새로 생성한 강한 비밀번호>
JWT_SECRET=<48자 이상 랜덤 문자열>
# 아래는 선택 - 키가 있으면 해당 기능이 동작, 없어도 앱은 기동함
#LLM_API_KEY=
#CLOVA_API_KEY_ID=
#CLOVA_API_KEY=
#FINMAP_API_KEY=
#SEOUL_API_KEY=
#JUSO_CONFIRMATION_KEY=
EOF
chmod 600 /opt/piggyback/.env
```
`.env`는 scp 동기화 대상 밖(`/opt/piggyback/`)이라 재배포에 덮이지 않습니다.

### 5. GitHub Secrets 등록 (repo Settings → Secrets → Actions)
| Secret | 값 |
|---|---|
| `EC2_HOST` | Elastic IP |
| `EC2_USER` | `deploy` |
| `EC2_SSH_KEY` | 배포 전용 키페어의 개인키 |
| `EC2_SSH_PORT` | `2222` |

⚠️ `dev`에 push할 수 있는 사람은 이 키로 서버 제어가 가능합니다(docker 그룹 ≈ host root). **dev 브랜치 보호(PR+리뷰) 권장.**

### 6. GHCR 패키지 가시성 (첫 빌드 후 1회)
첫 workflow 성공 후 `github.com/orgs/eobuba-official/packages`에서 `backend` 패키지 확인:
- **public 전환 (권장)** — EC2에 레지스트리 크레덴셜이 필요 없어짐. CI 빌드 이미지에는 시크릿이 없음(로컬 전용 `application-local.properties`는 CI checkout에 존재하지 않음)
- private 유지 시 — EC2에서 `docker login ghcr.io` (classic PAT, `read:packages`) 필요. **방치하면 첫 배포의 pull이 거부됨**

## 운영

### 배포 확인
- `http://<Elastic-IP>` → Swagger로 리다이렉트, `http://<Elastic-IP>/swagger-ui.html` 200
- seed 적재 확인 (EC2, `/opt/piggyback/deploy`에서):
```bash
docker compose --env-file ../.env -f docker-compose.prod.yml exec -T mysql \
  sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e "SELECT COUNT(*) FROM piggyback.task_type"'
# 기대값: 8
```

### 헬스 게이트 실패 시 (workflow 빨간불)
자동 롤백은 없습니다. 서버는 마지막 상태로 유지되며, 직전 커밋 sha로 수동 롤백:
```bash
cd /opt/piggyback/deploy
IMAGE_TAG=<직전 커밋 sha> docker compose --env-file ../.env -f docker-compose.prod.yml up -d
```
(336h prune 정책으로 직전 이미지는 로컬에 남아 있음. 롤백은 앱 이미지만 — compose/nginx 설정은 최신 유지)

### 로그·메모리
```bash
docker compose --env-file ../.env -f docker-compose.prod.yml logs -f app
free -m          # available 컬럼 기준
docker stats --no-stream
```
무부하 available < 150MB이면: ① mysql buffer pool 32M ② app mem_limit 512m ③ 스왑 3GB 순으로 완화.
그래도 부족하면 RDS 분리/t3.small 업그레이드 검토 (비용 발생 — 팀 합의 필요).

### HTTPS 추가 (추후)
도메인 준비 후 nginx에 443 server 블록 + Let's Encrypt 인증서만 추가하면 됩니다.
`X-Forwarded-Proto` 전달과 `server.forward-headers-strategy=framework`가 이미 적용되어 앱 재작업 없음.

## 주의
- **배포 이미지는 CI 빌드만** — 로컬에서 빌드한 이미지는 jar 안에 `application-local.properties`(실제 시크릿)가 포함되므로 GHCR에 push 금지
- 스키마는 initdb로 최초 1회만 적재 — 이후 엔티티 변경은 `ddl-auto=update`가 반영, 마스터 데이터 변경은 수동 SQL 또는 볼륨 재생성(`down -v`, 데이터 소실 주의)
