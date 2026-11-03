# kattok-server

카똑 백엔드 서버

사용자가 보유한 카드의 검증된 혜택 규칙과 실적·한도 조건을 바탕으로, 결제 상황에서 유리한 카드와 예상 혜택을 비교·추천합니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator) |
| 빌드 | Gradle 9 (Wrapper, Kotlin DSL) |
| DB | PostgreSQL 17 |
| 마이그레이션 | Flyway |
| 테스트 | JUnit 5, Testcontainers |

## 개발 환경

- JDK 21
- Docker (Compose 포함)

Gradle은 Wrapper(`./gradlew`)를 사용하므로 따로 설치하지 않아도 됩니다. 기본 `java`가 21이 아니어도 Gradle이 설치된 JDK 21을 찾아 빌드합니다.

클론한 뒤 Git 훅을 한 번 활성화해 주세요. 커밋 메시지와 브랜치 이름을 로컬에서 검사합니다.

```bash
git config core.hooksPath .githooks
```

## 로컬 실행

1. PostgreSQL을 실행합니다.

```bash
docker compose up -d
```

2. 서버를 실행합니다. 별도 설정이 없으면 `local` 프로필로 실행됩니다.

```bash
./gradlew bootRun
```

3. 서버 상태를 확인합니다. `{"groups":["liveness","readiness"],"status":"UP"}`처럼 `status`가 `UP`이면 정상입니다.

```bash
curl http://localhost:8080/actuator/health
```

### 로컬 DB 정보

| 항목 | 값 |
| --- | --- |
| Host / Port | `localhost:5433` |
| Database | `kattok` |
| User / Password | `kattok` / `kattok` |

다른 로컬 PostgreSQL과 겹치지 않도록 호스트 포트는 5433을 사용합니다. 5433도 사용 중이면 `DB_PORT`로 바꿀 수 있습니다. DB 실행과 서버 실행에 같은 값을 넘겨주세요.

```bash
DB_PORT=5434 docker compose up -d
```

```bash
DB_PORT=5434 ./gradlew bootRun
```

DB를 중지하거나, 데이터까지 지우고 초기화할 때는 다음 명령을 사용합니다.

```bash
docker compose down
```

```bash
docker compose down -v
```

### DB 스키마 변경

스키마는 Flyway로만 변경합니다. JPA는 시작할 때 엔티티와 스키마가 일치하는지 검증만 하고(`ddl-auto: validate`), 스키마를 직접 바꾸지 않습니다.

`src/main/resources/db/migration`에 `V<번호>__<설명>.sql` 파일을 추가하면 서버 시작 시 적용됩니다. 이미 머지된 마이그레이션 파일은 수정하지 말고 새 파일을 추가해 주세요.

## 테스트

Testcontainers가 임시 PostgreSQL을 띄우므로 Docker가 실행 중이어야 합니다. `docker compose`로 DB를 미리 띄울 필요는 없습니다.

```bash
./gradlew test
```

## 브랜치 규칙

`main`을 기준으로 작업 브랜치를 만들고, PR로 머지합니다.

- `main`: 항상 빌드와 테스트가 통과하는 상태를 유지합니다. 직접 푸시하지 않습니다.
- 작업 브랜치: `<type>/<이슈번호>-<kebab-case 설명>` 형식을 사용합니다. 이슈가 없으면 번호는 생략할 수 있습니다. `type`은 커밋 메시지와 같습니다.

```text
feat/12-card-search
fix/34-benefit-limit-calculation
chore/project-setup
```

작업 흐름은 다음과 같습니다.

1. 최신 `main`에서 작업 브랜치를 만듭니다.
2. 작업 후 `main`으로 PR을 올립니다.
3. 팀원 1명 이상의 승인을 받고, 테스트 통과를 확인한 뒤 Squash and merge로 머지합니다. PR 제목은 커밋 메시지 규칙을 따릅니다.
4. 머지한 브랜치는 삭제합니다.

## 커밋 메시지 규칙

```text
<type>(<scope>): <제목>

<본문(선택)>
```

| type | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 코드 개선 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 변경 |
| `chore` | 설정 등 기타 작업 |
| `build` | 빌드·의존성 변경 |
| `ci` | CI 설정 변경 |

- `scope`는 선택이며, 영어 소문자로 변경 영역을 적습니다. (예: `card`, `recommendation`, `payment`)
- 제목은 한글로 50자 이내로 간결하게 쓰고, 끝에 마침표를 붙이지 않습니다.
- 본문에는 무엇을 왜 바꿨는지 적습니다.

```text
feat(card): 보유 카드 등록 API 추가
fix(recommendation): 미확인 실적을 충족으로 계산하는 문제 수정
docs: 로컬 실행 방법 추가
```
