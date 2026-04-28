# CLAUDE.md — common-auth-lib

Spring Boot 마이크로서비스용 Keycloak 공통 인증 라이브러리.
**GitHub Packages**로 배포되며, 각 서비스는 Maven 의존성으로 추가한다.

## Build & Run Commands

```bash
./gradlew build          # 빌드 + 테스트
./gradlew test           # 테스트만
./gradlew compileJava    # 컴파일만
./gradlew publish        # GitHub Packages 배포 (환경변수 필요)
```

## 기술 스택

- Java 17 / Spring Boot 3.3.4 (Spring Security 6.3.x)
- Keycloak Authorization Code Flow
- `java-library` + `maven-publish` 플러그인
- group: `com.mycompany`, artifact: `common-auth-lib`, version: `1.0.0`

---

## 패키지 구조

```
src/main/java/com/hubilon/auth/
├── KeycloakAutoConfiguration.java      ← Spring Boot 자동 설정 진입점
├── KeycloakProperties.java             ← application.yml 설정값 바인딩
├── KeycloakClient.java                 ← Keycloak HTTP 클라이언트
├── KeycloakTokenFilter.java            ← JWT 검증 미들웨어 (OncePerRequestFilter)
├── SecurityConfig.java                 ← Spring Security + CSRF 설정
├── UserInfo.java                       ← 인증된 유저 정보 모델
├── UserContext.java                    ← ThreadLocal 유저 컨텍스트
├── TokenResponse.java                  ← Keycloak 토큰 응답 DTO
├── CurrentUser.java                    ← @CurrentUser 파라미터 어노테이션
├── CurrentUserArgumentResolver.java    ← @CurrentUser 주입 처리기
└── example/
    └── AuthController.java             ← 각 서비스에 복사해 쓰는 컨트롤러 예시

src/main/resources/
└── META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
        ← KeycloakAutoConfiguration 자동 등록

src/test/java/com/hubilon/auth/
├── KeycloakClientTest.java             ← URL 생성 로직 단위 테스트
└── KeycloakTokenFilterTest.java        ← 필터 동작 단위 테스트 (JwtDecoder mock)
```

---

## 클래스별 역할 및 설계 결정

### KeycloakAutoConfiguration
- `@AutoConfiguration` — `META-INF/spring/*.imports`에 등록되어 라이브러리 포함 시 자동 활성화
- `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnClass(SecurityFilterChain.class)` 조건부 활성화
- 등록 빈: `JwtDecoder` (NimbusJwtDecoder, JWKS URI 기반), `KeycloakTokenFilter`, `KeycloakClient`
- `SecurityConfig`를 `@Import`로 포함

### KeycloakProperties (`keycloak.*`)

| 설정 키 | 기본값 | 설명 |
|---|---|---|
| `server-url` | — | Keycloak 서버 주소 |
| `realm` | — | Realm 이름 |
| `client-id` | — | 클라이언트 ID |
| `client-secret` | — | 클라이언트 시크릿 |
| `redirect-uri` | — | Authorization Code 콜백 URI |
| `post-logout-redirect-uri` | redirect-uri 값 | 로그아웃 후 이동 URI |
| `post-login-redirect-uri` | `/` | 로그인 완료 후 이동 URI |
| `scope` | `openid profile email` | OIDC scope |
| `secure-cookie` | `true` | 쿠키 Secure 플래그 (HTTP 개발 시 false) |
| `permit-all-paths` | `[]` | 인증 없이 허용할 Ant 패턴 경로 목록 |

내부 URI 조합 메서드: `getAuthorizationUri()`, `getTokenUri()`, `getLogoutUri()`, `getJwksUri()`, `getIssuerUri()`

쿠키 이름 상수: `ACCESS_TOKEN_COOKIE = "access_token"`, `REFRESH_TOKEN_COOKIE = "refresh_token"`

### KeycloakClient
Authorization Code Flow에서 Keycloak Token Endpoint와 통신하는 HTTP 클라이언트.

| 메서드 | 설명 |
|---|---|
| `getAuthorizationUrl(state)` | Keycloak 로그인 페이지 URL 반환. state는 UUID로 CSRF 방지 |
| `handleCallback(code)` | authorization code → access/refresh/id token 교환 |
| `refreshToken(refreshToken)` | refresh token으로 새 access token 발급 |
| `getLogoutUrl(idToken)` | SSO 세션 종료 리다이렉트 URL 반환 (id_token_hint 포함) |
| `revokeToken(refreshToken)` | 백채널 토큰 폐기 (브라우저 없이 서버에서 직접 무효화) |

내부 예외: `KeycloakClient.KeycloakAuthException` (RuntimeException)

### KeycloakTokenFilter
`OncePerRequestFilter` 구현체. 모든 API 요청의 JWT를 검증한다.

**토큰 추출 우선순위:**
1. `Authorization: Bearer {token}` 헤더 (API 클라이언트 / 모바일)
2. `access_token` HttpOnly 쿠키 (브라우저)

**동작 흐름:**
```
요청 수신
  → shouldNotFilter(): permit-all-paths 해당 시 통과
  → extractToken(): 헤더 or 쿠키에서 토큰 추출
  → JwtDecoder.decode(): JWKS로 서명 검증 + 만료 확인
  → buildUserInfo(): JWT claims → UserInfo (userId, username, email, roles)
  → UserContext.set(userInfo): ThreadLocal 저장
  → 요청 통과 (finally: UserContext.clear())
  → 실패 시: 401 JSON 응답
```

JWT claim 매핑:
- `sub` → `userId`
- `preferred_username` → `username`
- `email` → `email`
- `realm_access.roles` → `roles` (realm 롤)
- `resource_access.{clientId}.roles` → `roles` (클라이언트 롤, 중복 허용)

### SecurityConfig
`@ConditionalOnMissingBean(SecurityFilterChain.class)` — 소비 서비스에 커스텀 SecurityFilterChain이 없을 때만 기본 설정 활성화.

**CSRF 보호:**
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` — `XSRF-TOKEN` 쿠키 발급 (JS 읽기 가능)
- `SameSite=Strict` 커스터마이징 적용
- `CsrfTokenRequestAttributeHandler` — XOR 인코딩 없이 쿠키 원본값을 헤더(`X-XSRF-TOKEN`)로 그대로 전송 (SPA 친화적)
- GET/HEAD/OPTIONS/TRACE는 CSRF 검증 제외

**세션 정책:** `IF_REQUIRED` — OAuth state 파라미터를 HttpSession에 저장하기 위해 필요. JwtDecoder 기반 API 필터는 쿠키에서 JWT를 읽어 무상태로 동작.

`WebMvcConfigurer.addArgumentResolvers()` — `CurrentUserArgumentResolver` 등록.

### UserInfo / UserContext
`UserInfo`: 불변 객체. `userId`, `username`, `email`, `roles(List)`, `hasRole(String)`.

`UserContext`: `ThreadLocal<UserInfo>` 래퍼. 필터가 요청 시작에 set, finally 블록에서 반드시 clear.

```java
UserContext.getUserId()
UserContext.getEmail()
UserContext.getRoles()
UserContext.hasRole("admin")
UserContext.get()  // UserInfo 전체
```

### @CurrentUser / CurrentUserArgumentResolver
`@CurrentUser` 어노테이션이 붙은 `UserInfo` 타입 컨트롤러 파라미터에 `UserContext.get()`을 주입.

```java
@GetMapping("/me")
public UserInfo me(@CurrentUser UserInfo user) { ... }
```

### TokenResponse
Keycloak Token Endpoint 응답 DTO. Jackson `@JsonProperty` 매핑.

| 필드 | JSON 키 | 비고 |
|---|---|---|
| `accessToken` | `access_token` | API 호출용 |
| `refreshToken` | `refresh_token` | 재발급용 |
| `idToken` | `id_token` | SSO 로그아웃 id_token_hint용 |
| `expiresIn` | `expires_in` | 초 단위 |
| `refreshExpiresIn` | `refresh_expires_in` | 초 단위 |

### example/AuthController
각 서비스가 복사해서 쓰는 컨트롤러 템플릿. 패키지명만 변경하면 사용 가능.

| 엔드포인트 | 설명 |
|---|---|
| `GET /auth/login` | state를 세션에 저장 후 Keycloak 로그인 페이지로 리다이렉트 |
| `GET /auth/callback` | state 검증 → code→token 교환 → HttpOnly 쿠키 발급 → postLoginRedirectUri로 이동 |
| `POST /auth/logout` | 쿠키 삭제 + Keycloak SSO 세션 만료 리다이렉트 |
| `POST /auth/refresh` | refresh_token 쿠키 읽어 새 access_token 쿠키 발급. 응답 바디 없음(204) |

쿠키 설정: `HttpOnly=true`, `SameSite=Strict`, `secure=properties.isSecureCookie()`, `path=/`

---

## 인증 전체 흐름

```
1. GET /auth/login
   → state = UUID → session["oauth_state"] = state
   → 302 redirect → Keycloak /auth?response_type=code&client_id=...&state=...

2. 사용자가 Keycloak 로그인 페이지에서 인증

3. GET /auth/callback?code=xxx&state=yyy
   → session["oauth_state"] == state 검증
   → POST Keycloak /token (grant_type=authorization_code, code=xxx)
   → Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Strict
   → Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict
   → session["id_token"] = id_token
   → 302 redirect → postLoginRedirectUri (/)

4. GET /api/anything
   → KeycloakTokenFilter: cookies["access_token"] → JWT 검증 → UserContext 설정
   → 컨트롤러 실행

5. POST /auth/refresh  (X-XSRF-TOKEN 헤더 필요)
   → cookies["refresh_token"] 서버에서 직접 읽기
   → Keycloak /token (grant_type=refresh_token)
   → 새 access_token, refresh_token 쿠키 재발급

6. POST /auth/logout  (X-XSRF-TOKEN 헤더 필요)
   → access_token, refresh_token 쿠키 삭제 (maxAge=0)
   → session.invalidate()
   → 302 redirect → Keycloak /logout?id_token_hint=...&post_logout_redirect_uri=...
```

---

## GitHub Packages 배포 구조

```
.github/workflows/publish.yml
  트리거: push to main
  환경변수:
    GITHUB_ACTOR      → github.actor (자동 주입)
    GITHUB_TOKEN      → secrets.GITHUB_TOKEN (자동 주입, packages:write 권한)
    GITHUB_REPOSITORY → github.repository (자동 주입, 'owner/repo' 형식)
  실행: ./gradlew build → ./gradlew publish
```

`build.gradle` publishing 블록에서 URL은 `GITHUB_REPOSITORY` 환경변수로 조합:
```
https://maven.pkg.github.com/${GITHUB_REPOSITORY}
```
owner/repo를 소스에 하드코딩하지 않는다.

## 소비 서비스 설정

소비 서비스 `build.gradle`:
```groovy
repositories {
    mavenCentral()
    maven {
        url = uri('https://maven.pkg.github.com/your-org/common-auth-lib')
        credentials {
            username = System.getenv('GITHUB_ACTOR') ?: ''
            password = System.getenv('GITHUB_TOKEN') ?: ''
        }
    }
}
dependencies {
    implementation 'com.mycompany:common-auth-lib:1.0.0'
}
```

로컬 개발 시 환경변수 설정 필요 (`read:packages` 권한 PAT):
```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxx
```

---

## 소비 서비스 필수 설정

```yaml
keycloak:
  server-url: http://keycloak:8080
  realm: my-realm
  client-id: my-service
  client-secret: xxxxxxxx
  redirect-uri: http://my-service/auth/callback
  post-logout-redirect-uri: http://my-service/auth/login
  post-login-redirect-uri: /
  secure-cookie: true        # 로컬 HTTP 개발 시 false
  permit-all-paths:
    - /auth/login
    - /auth/callback
    - /public/**
    - /health
```

---

## 아키텍처 제약

- `KeycloakTokenFilter`는 `Authorization` 헤더 → `access_token` 쿠키 순으로 토큰을 찾는다. 순서를 바꾸지 말 것.
- `UserContext.clear()`는 반드시 `finally` 블록에서 호출해야 한다 (ThreadLocal 누수 방지).
- `SecurityConfig`의 `CSRF` 설정을 제거하거나 `csrf.disable()`로 되돌리지 말 것.
- `example/AuthController`는 라이브러리 코드가 아닌 복사용 템플릿이다. 라이브러리 로직에 의존하는 변경은 `com.hubilon.auth` 패키지에서 처리한다.
- `TokenResponse`의 `idToken`은 로그아웃에만 사용한다. 서버 세션(`session["id_token"]`)에 보관하고 응답 바디로 노출하지 않는다.
