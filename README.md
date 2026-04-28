# common-auth-lib

Spring Boot 마이크로서비스에서 Keycloak **Authorization Code Flow** 인증을 공통으로 처리하는 라이브러리입니다.
**Git Submodule** 방식으로 배포 없이 바로 사용합니다.

## 인증 흐름

```
사용자 → GET /auth/login
       → Keycloak 로그인 페이지 (커스텀 테마) 리다이렉트
       → 로그인 완료 → GET /auth/callback?code=xxx&state=yyy
       → code → Access Token + Refresh Token + ID Token 교환
       → 이후 API 요청: Authorization: Bearer {access_token}
                        ↓
                 KeycloakTokenFilter (자동 JWT 검증)
```

---

## Git Submodule 설정

### 1. 서브모듈 추가

각 서비스 저장소 루트에서 실행합니다.

```bash
# libs 디렉토리에 추가 (경로는 서비스 정책에 따라 변경 가능)
git submodule add https://github.com/your-org/common-auth-lib.git libs/common-auth-lib
git commit -m "chore: add common-auth-lib submodule"
```

추가 후 서비스 디렉토리 구조:
```
my-service/
├── libs/
│   └── common-auth-lib/     ← submodule
├── src/
├── build.gradle
└── settings.gradle
```

### 2. settings.gradle 설정

`includeBuild`를 사용하면 Maven 배포 없이 Gradle이 로컬 빌드로 자동 대체합니다.

```groovy
// settings.gradle
rootProject.name = 'my-service'

// common-auth-lib를 composite build로 포함
includeBuild 'libs/common-auth-lib'
```

### 3. build.gradle 설정

```groovy
// build.gradle
dependencies {
    // includeBuild 선언으로 Gradle이 libs/common-auth-lib 빌드를 자동으로 참조
    implementation 'com.hubilon:common-auth-lib'
}
```

Maven에 배포하거나 `publishToMavenLocal`을 실행할 필요가 없습니다.

### 4. application.yml 설정

```yaml
keycloak:
  server-url: http://keycloak-server:8080
  realm: my-realm
  client-id: my-service
  client-secret: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  redirect-uri: http://my-service/auth/callback
  post-logout-redirect-uri: http://my-service/auth/login
  scope: openid profile email          # 기본값, 생략 가능
  permit-all-paths:
    - /auth/login
    - /auth/callback
    - /public/**
    - /health
    - /actuator/**
```

---

## 서브모듈 업데이트

### common-auth-lib에 변경사항이 생겼을 때

```bash
# 1. common-auth-lib 최신 커밋으로 업데이트
git submodule update --remote libs/common-auth-lib

# 2. 변경사항 확인
git diff libs/common-auth-lib

# 3. 서비스 저장소에 커밋
git add libs/common-auth-lib
git commit -m "chore: update common-auth-lib to latest"
```

### 모든 서브모듈 일괄 업데이트

```bash
git submodule update --remote --merge
```

---

## 팀원이 처음 클론할 때

서브모듈은 기본적으로 비어있는 상태로 클론됩니다.

```bash
# 방법 1: 클론과 동시에 서브모듈 초기화 (권장)
git clone --recurse-submodules https://github.com/your-org/my-service.git

# 방법 2: 이미 클론된 경우
git submodule update --init --recursive
```

---

## 컨트롤러 추가

`src/main/java/com/hubilon/auth/example/AuthController.java`를 서비스에 복사합니다.

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final KeycloakClient keycloakClient;

    // [STEP 1] Keycloak 로그인 페이지로 리다이렉트
    @GetMapping("/login")
    public void login(HttpSession session, HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        session.setAttribute("oauth_state", state);
        response.sendRedirect(keycloakClient.getAuthorizationUrl(state));
    }

    // [STEP 2] 콜백: code → 토큰 교환
    @GetMapping("/callback")
    public ResponseEntity<TokenResponse> callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session) {

        String savedState = (String) session.getAttribute("oauth_state");
        session.removeAttribute("oauth_state");
        if (savedState == null || !savedState.equals(state)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid state parameter");
        }

        TokenResponse tokens = keycloakClient.handleCallback(code);
        if (tokens.getIdToken() != null) {
            session.setAttribute("id_token", tokens.getIdToken());
        }
        return ResponseEntity.ok(tokens);
    }

    // [STEP 3] 로그아웃 (Keycloak SSO 세션까지 종료)
    @PostMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse response) throws IOException {
        String idToken = (String) session.getAttribute("id_token");
        session.invalidate();
        response.sendRedirect(keycloakClient.getLogoutUrl(idToken != null ? idToken : ""));
    }

    // Access Token 재발급
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(keycloakClient.refreshToken(request.refreshToken()));
    }

    public record RefreshRequest(String refreshToken) {}
}
```

---

## 현재 유저 정보 조회

```java
// 방법 1: @CurrentUser 어노테이션
@GetMapping("/api/me")
public ResponseEntity<UserInfo> me(@CurrentUser UserInfo user) {
    return ResponseEntity.ok(user);
}

// 방법 2: UserContext 정적 메서드
String userId = UserContext.getUserId();
String email  = UserContext.getEmail();
List<String> roles = UserContext.getRoles();
boolean isAdmin = UserContext.hasRole("admin");
```

---

## 커스텀 SecurityFilterChain

기본 설정 대신 직접 구성할 때:

```java
@Configuration
public class MySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    KeycloakTokenFilter filter) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("admin")
                .anyRequest().authenticated()
            )
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

---

## Keycloak Admin Console 설정

### 클라이언트 설정

```
Clients → {client-id} → Settings 탭
```

| 항목 | 값 |
|---|---|
| Client authentication | ON (confidential client) |
| Authentication flow | Standard flow ✓ (Authorization Code) |
| Direct access grants | OFF (ROPC 비활성화) |
| Valid redirect URIs | `http://my-service/auth/callback` |
| Valid post logout redirect URIs | `http://my-service/auth/login` |
| Web origins | `http://my-service` |

> **Valid redirect URIs**는 `keycloak.redirect-uri`와 정확히 일치해야 합니다.

### 클라이언트별 커스텀 테마 적용

```
Clients → {client-id} → Login settings 탭
→ Login theme: {your-custom-theme} 선택
```

테마 파일 위치: `{keycloak-root}/themes/{theme-name}/login/`

---

## 제공 기능

| 클래스 | 역할 |
|---|---|
| `KeycloakClient` | Authorization URL 생성 / code→토큰 교환 / 로그아웃 URL / 토큰 재발급 |
| `KeycloakTokenFilter` | 모든 API 요청의 Bearer 토큰 자동 검증 |
| `SecurityConfig` | Spring Security 필터 자동 등록 |
| `UserContext` | ThreadLocal 유저 정보 (userId, email, roles) |
| `KeycloakProperties` | `application.yml` 설정값 바인딩 |
| `@CurrentUser` | 컨트롤러 파라미터 유저 정보 주입 |
| `AuthController` (example) | 각 서비스에 복사해서 쓰는 예시 컨트롤러 |

## JWT 클레임 → UserInfo 매핑

| Keycloak JWT 클레임 | UserInfo 필드 |
|---|---|
| `sub` | `userId` |
| `preferred_username` | `username` |
| `email` | `email` |
| `realm_access.roles` | `roles` (realm 롤) |
| `resource_access.{client-id}.roles` | `roles` (클라이언트 롤) |
