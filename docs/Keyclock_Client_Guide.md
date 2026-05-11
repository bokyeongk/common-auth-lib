# Keyclock Client Guide
 각 서비스에서 해당 라이브러리를 사용하기 위해 Keyclock을 추가하는 방법에 대한 가이드입니다.

---

## 1. Keyclock Client 추가
### 1-1. Manage Realms → 해당 Realm 선택 → Clients → Create client

    1. General Settings
        - Client type: OpenID Connect
        - Client ID: my-service (서비스 이름)

    2. Capability config
        - Client authentication: On // 백엔드 서버 통신 위해 필요
        - Authorization: On // 권한 관리 위해 필요
        - Authentication flow: Standard flow
    3. Access settings
        - Root URL: http://my-service (서비스 URL)
        - Home URL: http://my-service (프론트 URL)
        - Valid redirect URIs: http://my-service/auth/callback (서비스 로그인 콜백 URL)
        - Valid post logout redirect URIs: http://my-service/auth/login (서비스 로그인 URL)
        - Admin URL: http://my-service (서비스 URL)


### 1-2. Credentials 탭 → Client secret 복사
    application.yml 설정 시 필요
    keycloak.client-secret: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
---
## 2. Role 추가

### 2-1. Client scopes 탭 → my-service-dedicated → role 기능 추가
    Add mapper → From predefined mappers → 하단 목록 선택 → Add
    - realm roles
    - client roles
    - groups

---
## 3. attribute 추가
### 3-1. User Profile Attribute 추가 (공통)
Realm settings → User profile → Create attribute
        
    Attribute[Name]: attribute 이름
    Display name: attribute 이름
    각자에 맞는 선택

### 3-2. JWT 클레임 설정
#### 3-2-1. Realm 레벨 
Client scope 추가 : Client scopes → Create client scope

    Name: scope 이름 (예: attribute scope) 
    Type: Default (토큰 자동 포함) # 기존 Client scope엔 추가해야함
Mapper 추가 : Client scopes → 해당 scope 선택 → Mappers 탭 → Configure new mapper → User attribute
 
    Name: mapper 이름 (예: attribute mapper)
    Mapper Type: User Attribute (방금 생성한 attribute 선택)
    User Attribute: attribute 이름
    Token Claim Name: attribute 이름 (JWT에 들어갈 필드명)
    Claim JWON Type: 각자에 맞는 선택
    Add to ID token: On
    Add to access token: On

> ⚠️ 기존 Client에는 수동으로 추가해야 합니다. (신규 Client에는 자동 포함)
>
> `Clients` → 해당 Client 선택 → `Client scopes` 탭 → `Add client scope` → 생성한 scope 선택
> - Type: `Default` (자동 포함)

### 3-2. Client 레벨
Clients → 해당 Client 선택 → Client scopes 탭 → my-service-dedicated → Mappers 탭 → Add mapper (By configuration) → User attribute

    Name: mapper 이름 (예: attribute mapper)
    Mapper Type: User Attribute (방금 생성한 attribute 선택)
    User Attribute: attribute 이름
    Token Claim Name: attribute 이름 (JWT에 들어갈 필드명)
    Claim JWON Type: 각자에 맞는 선택
    Add to ID token: On
    Add to access token: On

---
## 4. 회원 가입 자격
### 4-1. Clients → 해당 Client 선택 → Settings 탭 → Capability config → Service account roles On → Save
    → Service account roles 탭 → realm-management → manage-users 역할 추가
### 4-2. Service account roles 탭 → Assign roles → Client roles
    manage-users 역할 추가


---

### 1-2. Settings 탭 → Login settings → Login theme 선택 → Save
    휴빌론 테스트 서버 테마 위치 : /opt/keycloak-data/themes/
    추가 시 /opt/keycloak-data/themes/ 디렉토리에 테마 폴더를 생성 후 테마 파일 업로드 → Keycloak 재시작
    > docker restart keycloak-sso

