# mdmappbasic-backend (Kotlin + Ktor)

## Run

- Open this folder in IntelliJ IDEA as a Gradle project
- Run `com.example.mdmbackend.ApplicationKt`

Default: http://localhost:8080

## Seed accounts

- Admin: `admin / admin123`
- Device: `device / device123`

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
