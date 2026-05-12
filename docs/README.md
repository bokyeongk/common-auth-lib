# 서비스 연동 가이드

---
## 목차

1. [Backend 설정](#1-backend-설정)
2. [Frontend 설정](#2-frontend-설정)
3. [인증 흐름 요약](#3-인증-흐름-요약)
4. [CSRF 토큰 처리 규칙](#4-csrf-토큰-처리-규칙)
5. [REST API 직접 로그인 (ROPC)](#5-rest-api-직접-로그인-ropc)

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
    register: /auth/register
    checkUsername: /auth/check-username
    checkEmail: /auth/check-email
```

> `keycloak.uri.*`에 설정된 경로는 라이브러리가 자동으로 `permitAll()` 처리합니다.
> 
> 엔드포인트 동작을 직접 제어해야 하는 경우 `keycloak.auth-controller.enable` 를 `false` 로 설정하고 직접 구현합니다.
> 
> ** `AuthController.java`를 참고


### 1-4. 자동 등록 API 목록
>
> keycloak.auth-controller.enabled = true 일 경우 아래 엔드포인트가 라이브러리에 내장되어 별도 코드 없이 자동 등록됩니다.
>
| Method | Path | 파라미터 | 설명 |
|---|---|---|---|
| GET | `/auth/login` | - | Keycloak 로그인 페이지로 리다이렉트 |
| POST | `/auth/login` | Body: `{ username, password }` | ROPC 직접 로그인 |
| GET | `/auth/callback` | Query: `code`, `state` | Authorization Code 콜백 처리 및 토큰 쿠키 발급 |
| GET | `/auth/logout` | - | 쿠키 삭제 후 Keycloak SSO 세션 종료 리다이렉트 |
| POST | `/auth/refresh` | - | refresh token 쿠키로 access token 갱신 |
| POST | `/auth/register` | Body: `{ username, password, email, firstName?, lastName?, attributes? }` | 신규 회원가입 |
| GET | `/auth/check-username` | Query: `username` | username 중복 여부 확인 |
| GET | `/auth/check-email` | Query: `email` | email 중복 여부 확인 |

> 경로 변경: `keycloak.uri.*` 설정으로 각 경로를 재정의할 수 있습니다.



### 1-5. 로그인 계정 정보 조회

#### 토큰에 포함된 정보 — `UserContext`

JWT 검증 통과 시 자동으로 세팅됩니다. 추가 네트워크 호출 없음.

| 메소드 | 반환 | 설명 |
|---|---|---|
| `UserContext.getUserId()` | `String` | 사용자 고유 ID (`sub`) |
| `UserContext.getEmail()` | `String` | 이메일 |
| `UserContext.getRoles()` | `List<String>` | 역할 목록 |
| `UserContext.hasRole(role)` | `boolean` | 특정 역할 보유 여부 |
| `UserContext.get()` | `UserInfo` | 전체 사용자 정보 객체 |
| `UserContext.get().getAttributes()` | `Map<String, Object>` | JWT에 포함된 커스텀 attribute |

```java
// @CurrentUser 어노테이션으로 주입
@GetMapping("/api/me")
public UserInfo me(@CurrentUser UserInfo user) {
    return user;
}

// UserContext 정적 메소드로 직접 접근
String userId  = UserContext.getUserId();
String email   = UserContext.getEmail();
boolean isAdmin = UserContext.hasRole("admin");
String dept    = (String) UserContext.get().getAttributes().get("department");
```

---

#### 토큰에 포함되지 않은 정보 — `keycloakClient.getUserInfo(request)`

Keycloak `/userinfo` 엔드포인트를 직접 호출합니다. 호출 시마다 Keycloak 서버와 통신합니다.

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `request` | `HttpServletRequest` | 현재 HTTP 요청 (토큰 자동 추출) |

| 반환 | 설명 |
|---|---|
| `Map<String, Object>` | Keycloak에 등록된 전체 클레임 (scope에 따라 다름) |

```java
@GetMapping("/api/me/detail")
public ResponseEntity<?> detail(HttpServletRequest request) {
    Map<String, Object> info = keycloakClient.getUserInfo(request);
    String telNo = (String) info.get("telNo");
    return ResponseEntity.ok(info);
}
```

> attribute를 `/userinfo`에만 포함하려면 Keycloak mapper 설정에서 `Add to access token: Off` / `Add to userinfo: On` 으로 설정합니다. ([Keyclock_Client_Guide.md](Keyclock_Client_Guide.md) 참고)


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

**케이스 (1) — Keycloak 로그인 화면으로 이동**

Keycloak이 제공하는 로그인 페이지로 리다이렉트합니다.

```typescript
window.location.href = 'http://my-service/auth/login';
```

> `axios` / `fetch` 등으로 직접 호출하면 서버가 Keycloak 로그인 페이지로 302 리다이렉트할 때 **CORS 오류가 발생**합니다.  
> 브라우저가 직접 이동하도록 `window.location.href`를 사용해야 합니다.

---

**케이스 (2) — 서비스 자체 로그인 화면 구현 후 API 호출**

서비스에서 로그인 폼을 직접 구현하고 `POST /auth/login`을 호출합니다.  
`XSRF-TOKEN` 쿠키 확보를 위해 로그인 화면 진입 시 GET 요청을 1회 선행합니다.

```typescript
// 로그인 화면 진입 시 1회 호출 — XSRF-TOKEN 쿠키 확보
await axios.get('/auth/login', { withCredentials: true, maxRedirects: 0 })
  .catch(() => {});

// 로그인 요청 (Axios — XSRF-TOKEN 헤더 자동 포함)
const response = await apiClient.post('/auth/login', {
  username: 'user@example.com',
  password: 'secret',
});
```

| 응답 코드 | 의미 |
|---|---|
| `200 OK` | 로그인 성공 — access/refresh token 쿠키 자동 발급 |
| `400 Bad Request` | XSRF-TOKEN 없음 |
| `401 Unauthorized` | username/password 오류 |

> **전제조건:** Keycloak Admin → 해당 Client → `Direct Access Grants Enabled = ON`

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
