# 서비스 연동 가이드

---
## 목차

1. [Backend 설정](#1-backend-설정)
2. [Frontend 설정](#2-frontend-설정)
3. [인증 흐름 요약](#3-인증-흐름-요약)
4. [CSRF 토큰 처리 규칙](#4-csrf-토큰-처리-규칙)

---
## 1. Backend 설정
### 1-1. common-auth-lib.jar 추가
```
/libs/common-auth-lib-1.0.0.jar
```

### 1-2. Spring Security, OAuth2 Resource Server 의존성 추가
```gradle
dependencies {
    implementation(files("libs/common-auth-lib-1.0.0.jar"))
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-security")
}
```

### 1-3. `application.yml`에 아래 설정 추가
```yaml
keycloak:
  server-url: http://192.168.10.30:8080          # Keycloak 서버 주소
  realm: my-realm                           # Realm 이름
  client-id: my-service                     # 클라이언트 ID
  client-secret: xxxxxxxx                   # 클라이언트 시크릿
  oauth-state-cookie: my_oauth_state       # OAuth 상태값 저장 쿠키 이름 (기본값: oauth_state)
  session-id-token-key: my_id_token       # 세션 ID로 사용할 토큰 키 (기본값: id_token)
  redirect-uri: http://my-service/auth/callback
  post-logout-redirect-uri: http://my-service/auth/login
  post-login-redirect-uri: /               # 로그인 완료 후 이동 경로
  secure-cookie: false                      # 로컬 HTTP 개발 시 false, 운영 HTTPS 시 true
  permit-all-paths:
    - /public/**
    - /health
    - /actuator/**
  # 인증 엔드포인트 자동 등록 (기본값: true, 명시 생략 가능)
  auth-controller:
    enabled: true
  # 인증 엔드포인트 경로 (기본값 사용 시 명시 생략 가능)
  uri:
    login: /auth/login
    callback: /auth/callback
    logout: /auth/logout
    refresh: /auth/refresh
```

> `keycloak.uri.*`에 설정된 경로는 라이브러리가 자동으로 `permitAll()` 처리합니다.


### 1-4. 컨트롤러 — 자동 등록 (코드 불필요)

`GET /auth/login`, `GET /auth/callback`, `GET /auth/logout`, `POST /auth/refresh` 엔드포인트가
라이브러리에 내장되어 **별도 코드 없이 자동 등록**됩니다.

#### 커스텀 컨트롤러가 필요한 경우

엔드포인트 동작을 직접 제어해야 하는 경우 라이브러리 컨트롤러를 비활성화하고 직접 구현합니다.

```yaml
keycloak:
  auth-controller:
    enabled: false   # 라이브러리 기본 컨트롤러 비활성화
```
`AuthController.java`를 참고해 직접 구현하세요.


### 1-5. 컨트롤러에서 유저 정보 사용

```java
// @CurrentUser 어노테이션으로 주입
@GetMapping("/api/me")
public UserInfo me(@CurrentUser UserInfo user) {
    return user;
}

// UserContext 정적 메서드로 직접 접근
@GetMapping("/api/data")
public ResponseEntity<?> getData() {
    String userId = UserContext.getUserId();
    List<String> roles = UserContext.getRoles();
    boolean isAdmin = UserContext.hasRole("admin");
    // ...
}
```
---
## 2. Frontend 설정

### 2-1. 필수 설정 항목

#### 1) 쿠키 자동 전송 (`withCredentials`)

HttpOnly 쿠키(access_token, refresh_token)를 자동으로 전송하려면 **모든 API 요청에 `withCredentials: true`** 를 설정해야 합니다.

```typescript
// Axios
const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true, // 필수
});

// fetch API
fetch('/api/data', {
  credentials: 'include', // 필수
});
```

---

#### 2) CSRF 헤더 자동 추가 (POST / PUT / DELETE / PATCH)

서버는 상태 변경 요청(`POST`, `PUT`, `DELETE`, `PATCH`)에 대해 `X-XSRF-TOKEN` 헤더를 검증합니다.  
토큰은 로그인 완료 후 서버가 발급하는 `XSRF-TOKEN` 쿠키에서 읽어 헤더에 추가해야 합니다.

> **Axios를 사용하는 경우** `xsrfCookieName` / `xsrfHeaderName` 기본값이 각각 `XSRF-TOKEN` / `X-XSRF-TOKEN`으로 설정되어 있어 **별도 설정 없이 자동 처리**됩니다.  
> 단, `withCredentials: true`가 설정되어 있어야 동작합니다.

다른 라이브러리나 `fetch`를 사용하는 경우 직접 쿠키를 읽어 헤더에 추가해야 합니다.

```typescript
// fetch 사용 시 직접 처리 예시
function getCookie(name: string): string {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
  return match ? decodeURIComponent(match[2]) : '';
}

fetch('/api/data', {
  method: 'POST',
  credentials: 'include',
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': getCookie('XSRF-TOKEN'), // 직접 추가
  },
  body: JSON.stringify(data),
});
```

---

#### 3) 로그인 페이지 이동

로그인이 필요한 경우 아래와 같이 `window.location.href`로 이동합니다.

```typescript
window.location.href = 'http://my-service/auth/login';
```

> `axios` / `fetch` 등으로 직접 호출하면 서버가 Keycloak 로그인 페이지로 302 리다이렉트할 때 **CORS 오류가 발생**합니다.  
> 브라우저가 직접 이동하도록 `window.location.href`를 사용해야 합니다.

---

#### 4) 로그아웃 처리

로그아웃도 동일하게 `window.location.href`로 이동합니다.

```typescript
window.location.href = 'http://my-service/auth/logout';
```

> 서버의 `/auth/logout`은 Keycloak SSO 세션 종료를 위해 Keycloak 로그아웃 URL로 302 리다이렉트합니다.  
> `axios` 등으로 직접 호출하면 크로스 오리진 리다이렉트 과정에서 **CORS 오류가 발생**하므로 반드시 `window.location.href`를 사용해야 합니다.

---

### 2-2. 401 처리 및 토큰 갱신

API 요청에서 `401` 응답을 받으면 `POST /auth/refresh`로 토큰 갱신을 시도한 뒤 원래 요청을 재시도하는 방식을 권장합니다.  
아래는 Axios 인터셉터를 활용한 구현 예시이며, **사용하는 라이브러리나 프로젝트 구조에 따라 동일한 흐름을 다르게 구현해도 무방합니다.**

```typescript
let isRefreshing = false;
let refreshQueue: Array<() => void> = [];

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/refresh')
    ) {
      originalRequest._retry = true;

      if (isRefreshing) {
        // 갱신 진행 중이면 완료 후 재요청
        return new Promise((resolve) => {
          refreshQueue.push(() => resolve(apiClient(originalRequest)));
        });
      }

      isRefreshing = true;
      try {
        await apiClient.post('/auth/refresh');
        refreshQueue.forEach((cb) => cb());
        refreshQueue = [];
        return apiClient(originalRequest);
      } catch {
        // 갱신 실패 → 로그인 페이지로 이동
        window.location.href = 'http://my-service/auth/login';
        return Promise.reject(error);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
```

**핵심 흐름:**
- `401` 수신 → `POST /auth/refresh` 호출 (refresh_token 쿠키 자동 전송)
- 갱신 성공 → 원래 요청 재시도
- 갱신 실패 → 로그인 페이지로 이동
- 갱신 중 추가 `401` 발생 시 → 갱신 완료 후 일괄 재시도

---
## 3. 인증 흐름 요약

```
[로그인]
브라우저 → GET /auth/login
         → 302 → Keycloak 로그인 페이지
         → 로그인 완료
         → GET /auth/callback?code=xxx&state=yyy
         → 서버: code → token 교환
         → Set-Cookie: access_token (HttpOnly)
         → Set-Cookie: refresh_token (HttpOnly)
         → Set-Cookie: XSRF-TOKEN (JS 읽기 가능)
         → 302 → postLoginRedirectUri (/)

[API 요청]
React → axios.get('/api/data', { withCredentials: true })
      → 자동으로 access_token 쿠키 전송
      → KeycloakTokenFilter: JWT 검증 → SecurityContextHolder 설정
      → 컨트롤러 실행

[토큰 만료 시]
React → 401 응답 수신
      → axios 인터셉터: POST /auth/refresh
      → 서버: refresh_token으로 새 access_token 발급
      → 원래 요청 재시도

[로그아웃]
브라우저 → GET /auth/logout
         → 서버: refresh_token 백채널 revoke → 쿠키 삭제
         → 302 → Keycloak 로그아웃 URL (id_token_hint 포함)
         → Keycloak: SSO 세션 종료
         → 302 → post-logout-redirect-uri (/auth/login)
```

---

## 4. CSRF 토큰 처리 규칙

| 엔드포인트 | CSRF 검증 | 이유 |
|---|---|---|
| `GET /auth/login` | 제외 (GET) | GET은 CSRF 대상 아님 |
| `GET /auth/callback` | 제외 (GET) | GET은 CSRF 대상 아님 |
| `GET /auth/logout` | 제외 (GET) | GET은 CSRF 대상 아님 |
| `POST /auth/refresh` | **제외** | 서버에서 `ignoringRequestMatchers` 처리 |
| `POST /api/**` (일반 API) | **필요** | `X-XSRF-TOKEN` 헤더 전송 필요 |

`XSRF-TOKEN` 쿠키는 `/auth/callback` 응답 시점에 발급됩니다.  
axios 인터셉터가 이 쿠키를 읽어 POST/PUT/DELETE/PATCH 요청에 자동으로 `X-XSRF-TOKEN` 헤더를 추가합니다.
