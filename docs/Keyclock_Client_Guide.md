# Keyclock Client Guide
 각 서비스에서 해당 라이브러리를 사용하기 위해 Keyclock Client를 추가하는 방법에 대한 가이드입니다.

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

### 1-2. Settings 탭 → Login settings → Login theme 선택 → Save
    휴빌론 테스트 서버 테마 위치 : /opt/keycloak-data/themes/
    추가 시 /opt/keycloak-data/themes/ 디렉토리에 테마 폴더를 생성 후 테마 파일 업로드 → Keycloak 재시작
    > docker restart keycloak-sso
    
    
### 1-2. Credentials 탭 → Client secret 복사
    application.yml 설정 시 필요
    keycloak.client-secret: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx

### 1-3. Client scopes 탭 → my-service-dedicated → role 기능 추가
    Add mapper → From predefined mappers → 하단 목록 선택 → Add
    - realm roles
    - client roles
    - groups