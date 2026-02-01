# mdmappbasic-backend (Kotlin + Ktor)

## Run

- Open this folder in IntelliJ IDEA as a Gradle project
- Run `com.example.mdmbackend.ApplicationKt`

Default: http://localhost:8080

## Seed accounts

- Admin: `admin / admin123`
- Device: `device / device123`

## Key endpoints

- POST `/api/auth/login`
- GET `/api/device/config/{userCode}` (needs Bearer token)
- Admin profiles: `/api/admin/profiles`

