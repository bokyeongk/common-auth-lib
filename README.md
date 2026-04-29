# common-auth-lib

Spring Boot 마이크로서비스용 Keycloak **Authorization Code Flow** 공통 인증 라이브러리.
**GitHub Packages**로 배포되며, 각 서비스는 일반 Maven 의존성처럼 추가합니다.

## 인증 흐름

```
사용자 → GET /auth/login
       → Keycloak 로그인 페이지 (커스텀 테마) 리다이렉트
       → 로그인 완료 → GET /auth/callback?code=xxx&state=yyy
       → code → Access Token + Refresh Token + ID Token 교환
       → HttpOnly 쿠키로 발급 (JS 접근 불가)
       → 이후 API 요청: access_token 쿠키 자동 전송
                        ↓
                 KeycloakTokenFilter (자동 JWT 검증)
```

---

## GitHub Packages 배포

### 자동 배포 (GitHub Actions)

`main` 브랜치에 push하면 자동으로 GitHub Packages에 배포됩니다.

```
.github/workflows/publish.yml
  → push to main
  → ./gradlew build (빌드 + 테스트)
  → ./gradlew publish (GitHub Packages 업로드)
```

### 수동 배포

```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx   # write:packages 권한 필요
export GITHUB_REPOSITORY=your-org/common-auth-lib

./gradlew publish
```

---

## 각 서비스에서 사용하는 방법

### 1. GitHub Token 발급

GitHub Packages 패키지를 읽으려면 `read:packages` 권한이 있는 토큰이 필요합니다.

```
GitHub → Settings → Developer settings
→ Personal access tokens → Tokens (classic)
→ Generate new token (classic)
→ 권한 체크: read:packages
→ 토큰 복사 (ghp_로 시작)
```

> CI 환경(GitHub Actions)에서는 `secrets.GITHUB_TOKEN`이 자동으로 제공되어 별도 발급 불필요.

### 2. 환경변수 설정

**로컬 개발 환경** — `~/.bashrc` 또는 `~/.zshrc`에 추가:

```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
```

**GitHub Actions CI** — `settings.yml`에 자동 주입되므로 별도 설정 불필요:

```yaml
env:
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  GITHUB_ACTOR: ${{ github.actor }}
```

### 3. build.gradle 설정

```groovy
repositories {
    mavenCentral()
    maven {
        name = 'GitHubPackages'
        url = uri('https://maven.pkg.github.com/your-org/common-auth-lib')
        credentials {
            username = System.getenv('GITHUB_ACTOR') ?: ''
            password = System.getenv('GITHUB_TOKEN') ?: ''
        }
    }
}

dependencies {
    implementation 'com.hubilon:common-auth-lib:1.0.0'
}
```

> `your-org/common-auth-lib` 부분을 실제 GitHub 조직명/저장소명으로 변경하세요.

### 4. application.yml 설정

```yaml
keycloak:
  server-url: http://keycloak-server:8080
  realm: my-realm
  client-id: my-service
  client-secret: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  redirect-uri: http://my-service/auth/callback
  post-logout-redirect-uri: http://my-service/auth/login
  post-login-redirect-uri: /               # 로그인 후 이동 경로
  secure-cookie: true                       # 로컬 HTTP 개발 시 false
  permit-all-paths:
    - /auth/login
    - /auth/callback
    - /public/**
    - /health
    - /actuator/**
```

### 5. 컨트롤러 추가

`src/main/java/com/hubilon/auth/example/AuthController.java`를 서비스에 복사합니다.
패키지명만 변경하면 바로 사용 가능합니다.

---

## 버전 업데이트 방법

### 1. build.gradle 버전 변경

```groovy
// common-auth-lib/build.gradle
version = '1.1.0'   // 버전 증가
```

### 2. main 브랜치에 push

```bash
git add build.gradle
git commit -m "chore: bump version to 1.1.0"
git push origin main
# → GitHub Actions가 자동으로 GitHub Packages에 배포
```

### 3. 소비 서비스에서 버전 업데이트

```groovy
// 각 서비스의 build.gradle
dependencies {
    implementation 'com.hubilon:common-auth-lib:1.1.0'  // 버전 변경
}
```

```bash
./gradlew build  # 새 버전 자동 다운로드
```

> GitHub Packages는 이미 배포된 버전 덮어쓰기를 허용하지 않습니다.
> 변경사항은 항상 버전을 올려서 배포하세요.

---

## 제공 기능

| 클래스 | 역할 |
|---|---|
| `KeycloakClient` | Authorization URL 생성 / code→토큰 교환 / 로그아웃 URL / 토큰 재발급 |
| `KeycloakTokenFilter` | 모든 API 요청의 Bearer 토큰 + HttpOnly 쿠키 자동 검증 |
| `SecurityConfig` | Spring Security + CSRF(CookieCsrfTokenRepository) 자동 등록 |
| `UserContext` | ThreadLocal 유저 정보 (userId, email, roles) |
| `KeycloakProperties` | `application.yml` 설정값 바인딩 |
| `@CurrentUser` | 컨트롤러 파라미터 유저 정보 주입 |
| `AuthController` (example) | 각 서비스에 복사해서 쓰는 컨트롤러 예시 |

## 보안 설계

| 항목 | 구현 |
|---|---|
| 토큰 저장 | `access_token`, `refresh_token` → HttpOnly 쿠키 (JS 접근 불가) |
| CSRF 보호 | `XSRF-TOKEN` 쿠키(JS 읽기 가능) + `X-XSRF-TOKEN` 헤더 검증 |
| 쿠키 속성 | `SameSite=Strict`, `Secure` (운영), `Path=/` |
| SSO 로그아웃 | `id_token_hint`로 Keycloak SSO 세션까지 완전 종료 |

## JWT 클레임 → UserInfo 매핑

| Keycloak JWT 클레임 | UserInfo 필드 |
|---|---|
| `sub` | `userId` |
| `preferred_username` | `username` |
| `email` | `email` |
| `realm_access.roles` | `roles` (realm 롤) |
| `resource_access.{client-id}.roles` | `roles` (클라이언트 롤) |

## 빌드

```bash
./gradlew build    # 빌드 + 테스트
./gradlew test     # 테스트만
```
