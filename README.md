# mdmappbasic-backend (Kotlin + Ktor)

## Run

- Open this folder in IntelliJ IDEA as a Gradle project
- Run `com.example.mdmbackend.ApplicationKt`
- Optional: set `MDM_PROFILE=local-dev`, `MDM_PROFILE=integration-test`, or `MDM_PROFILE=demo`

Default: http://localhost:8080

## Profiles

### `local-dev`

- Purpose: simple local development
- On a clean/new local MySQL schema, default admin login: `local-admin / local-admin-123`
- On a clean/new local MySQL schema, default device login: `local-device / local-device-123`
- Default unlock password: `2468`
- Default seeded profile on a clean/new local MySQL schema: `LOCAL001`
- If your local MySQL schema already contains older seed data, older accounts such as `admin / admin123` and `device / device123` may still exist
- `local-dev` seed inserts missing `local-admin`, `local-device`, and `LOCAL001`; it does not overwrite older seed users, passwords, or profiles already in MySQL
- Local DB can be overridden with `MDM_LOCAL_DB_JDBC_URL`, `MDM_LOCAL_DB_DRIVER`, `MDM_LOCAL_DB_USER`, `MDM_LOCAL_DB_PASSWORD`

### `integration-test`

- Purpose: repeatable test seed/config
- Admin login: `admin / admin123`
- Device login: `device / device123`
- Default unlock password: `1111`
- Selected automatically when tests override `mdm.db.*` to H2 and do not set `MDM_PROFILE`

### `demo`

- Purpose: shared demo environment without committed weak secrets
- Demo passwords and demo DB settings must be provided via env:
  - `MDM_DEMO_ADMIN_PASS`
  - `MDM_DEMO_DEVICE_PASS`
  - `MDM_DEMO_DEVICE_UNLOCK_PASS`
  - `MDM_DEMO_DB_JDBC_URL`
  - `MDM_DEMO_DB_DRIVER`
  - `MDM_DEMO_DB_USER`
  - `MDM_DEMO_DB_PASSWORD`
- Optional demo identifiers:
  - `MDM_DEMO_ADMIN_USER`
  - `MDM_DEMO_DEVICE_USER`
  - `MDM_DEMO_DEFAULT_USER_CODE`

## Local Postman Values

If your Postman collection is stored outside this repo, use these local-dev values for a clean/new local MySQL schema, or after first boot inserts the missing local-dev seed:

- `baseUrl = http://localhost:8080`
- `adminUsername = local-admin`
- `adminPassword = local-admin-123`
- `deviceUsername = local-device`
- `devicePassword = local-device-123`
- `unlockPassword = 2468`

## Backend API contract (current)

### Auth
- POST `/api/auth/login`
- POST `/api/auth/logout`

### Device (current standard)
- POST `/api/device/register`
- POST `/api/device/unlock`
- GET `/api/device/config/current?deviceCode=...`
- POST `/api/device/poll`
- POST `/api/device/ack`
- POST `/api/device/location`
- POST `/api/device/usage`
- POST `/api/device/usage/batch`
- POST `/api/device/apps/inventory`
- POST `/api/device/{deviceCode}/events`

### Device config deprecated compatibility route
- GET `/api/device/config/{userCode}?deviceCode=...`
    - Deprecated, fallback only
    - Response headers:
        - `X-Deprecated: true`
        - `Warning: 299 - "Deprecated endpoint. Use GET /api/device/config/current?deviceCode=..."`

### Admin - profiles
- GET `/api/admin/profiles`
- POST `/api/admin/profiles`
- GET `/api/admin/profiles/{id}`
- PUT `/api/admin/profiles/{id}`
- DELETE `/api/admin/profiles/{id}`
- PUT `/api/admin/profiles/{id}/allowed-apps`

### Admin - devices
- GET `/api/admin/devices`
- GET `/api/admin/devices/{id}`
- GET `/api/admin/devices/{id}/apps`
- PUT `/api/admin/devices/{id}/link`
- POST `/api/admin/devices/{id}/lock`
- POST `/api/admin/devices/{id}/reset-unlock-pass`
- GET `/api/admin/devices/{id}/location/latest`
- GET `/api/admin/devices/{id}/events?limit=...`
- GET `/api/admin/devices/{id}/usage/summary`

### Admin - commands
- POST `/api/admin/devices/{id}/commands`
- GET `/api/admin/devices/{id}/commands`
- POST `/api/admin/devices/{id}/commands/{commandId}/cancel`

### Admin - audit
- GET `/api/admin/audit?limit=...&offset=...&action=...&actorType=...`
