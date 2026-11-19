# HTTP evidence — browser QA run

Every request below was issued by the Angular UI through the local proxy. Bearer tokens and passwords are redacted.

## 001 — POST /api/platform/login

```bash
curl -i -X POST 'http://localhost:8080/api/platform/login' -H 'Content-Type: application/json' -d '{"email":"<redacted>","password":"<redacted>"}'
```

**Observed response:** `HTTP 200`

```json
{
  "token": "<redacted>",
  "tokenType": "<redacted>"
}
```

## 002 — GET /api/companies

```bash
curl -i -X GET 'http://localhost:8080/api/companies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "0eaa3a20-4b1b-4e12-be08-53d1f164c2f3",
    "legalName": "Peluqueria Estilo SAS",
    "displayName": "Peluqueria Estilo",
    "slug": "peluqueria-estilo",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-09-15T20:45:12.127342Z"
  },
  {
    "id": "36a7d8be-840b-432b-ab37-208b9e066163",
    "legalName": "Slotix isolated frontend QA",
    "displayName": "Frontend QA",
    "slug": "qa-e1994a6c-8b22-4051-ad0e-d8abfd1dfb3c",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:28:00.782503Z"
  },
  {
    "id": "e244500e-be20-4578-a486-15a8494c47bf",
    "legalName": "Slotix Browser QA qamvimoyjn S.A.S.",
    "displayName": "Slotix Browser QA qamvimoyjn",
    "slug": "browser-qamvimoyjn",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:32:51.051816Z"
  },
  {
    "id": "bde93665-f36b-453f-9302-8444f528f110",
    "legalName": "Slotix Browser QA qamvin1dsx S.A.S.",
    "displayName": "Slotix Browser QA qamvin1dsx",
    "slug": "browser-qamvin1dsx",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:42:30.094288Z"
  },
  {
    "id": "e27180fc-aa68-406f-a84e-7b340dee7343",
    "legalName": "Slotix Browser QA qamvin2aez S.A.S.",
    "displayName": "Slotix Browser QA qamvin2aez",
    "slug": "browser-qamvin2aez",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:43:12.361920Z"
  },
  {
    "id": "883fdd63-0e2d-4392-8be1-f1c5b8241a7f",
    "legalName": "Slotix Browser QA qamvin3ahb S.A.S.",
    "displayName": "Slotix Browser QA qamvin3ahb",
    "slug": "browser-qamvin3ahb",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:43:59.103223Z"
  },
  {
    "id": "63f983d3-c59f-47f7-91c1-733a71955364",
    "legalName": "Slotix Browser QA qamvin5jg7 S.A.S.",
    "displayName": "Slotix Browser QA qamvin5jg7",
    "slug": "browser-qamvin5jg7",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:45:44.002095Z"
  }
]
```

## 003 — POST /api/v1/companies/onboarding

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/onboarding' -H 'Content-Type: application/json' -d '{"company":{"legalName":"Slotix Browser QA qamvin6yii S.A.S.","displayName":"Slotix Browser QA qamvin6yii","slug":"browser-qamvin6yii","contactEmail":"<redacted>"},"administrator":{"newUser":{"email":"<redacted>","fullName":"Browser QA Administrator","password":"<redacted>"}}}'
```

**Observed response:** `HTTP 201`

```json
{
  "company": {
    "id": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "legalName": "Slotix Browser QA qamvin6yii S.A.S.",
    "displayName": "Slotix Browser QA qamvin6yii",
    "slug": "browser-qamvin6yii",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:46:50.269541Z"
  },
  "administrator": {
    "id": "6856e395-9f6a-4c1b-96db-1eb2c4cedb33",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "userId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
    "email": "<redacted>",
    "fullName": "Browser QA Administrator",
    "status": "ACTIVE",
    "roles": [
      "COMPANY_ADMIN"
    ],
    "joinedAt": "2026-10-21T21:46:50.369273Z",
    "createdAt": "2026-10-21T21:46:50.369273Z",
    "updatedAt": "2026-10-21T21:46:50.369273Z"
  }
}
```

## 004 — GET /api/companies

```bash
curl -i -X GET 'http://localhost:8080/api/companies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "0eaa3a20-4b1b-4e12-be08-53d1f164c2f3",
    "legalName": "Peluqueria Estilo SAS",
    "displayName": "Peluqueria Estilo",
    "slug": "peluqueria-estilo",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-09-15T20:45:12.127342Z"
  },
  {
    "id": "36a7d8be-840b-432b-ab37-208b9e066163",
    "legalName": "Slotix isolated frontend QA",
    "displayName": "Frontend QA",
    "slug": "qa-e1994a6c-8b22-4051-ad0e-d8abfd1dfb3c",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:28:00.782503Z"
  },
  {
    "id": "e244500e-be20-4578-a486-15a8494c47bf",
    "legalName": "Slotix Browser QA qamvimoyjn S.A.S.",
    "displayName": "Slotix Browser QA qamvimoyjn",
    "slug": "browser-qamvimoyjn",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:32:51.051816Z"
  },
  {
    "id": "bde93665-f36b-453f-9302-8444f528f110",
    "legalName": "Slotix Browser QA qamvin1dsx S.A.S.",
    "displayName": "Slotix Browser QA qamvin1dsx",
    "slug": "browser-qamvin1dsx",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:42:30.094288Z"
  },
  {
    "id": "e27180fc-aa68-406f-a84e-7b340dee7343",
    "legalName": "Slotix Browser QA qamvin2aez S.A.S.",
    "displayName": "Slotix Browser QA qamvin2aez",
    "slug": "browser-qamvin2aez",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:43:12.361920Z"
  },
  {
    "id": "883fdd63-0e2d-4392-8be1-f1c5b8241a7f",
    "legalName": "Slotix Browser QA qamvin3ahb S.A.S.",
    "displayName": "Slotix Browser QA qamvin3ahb",
    "slug": "browser-qamvin3ahb",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:43:59.103223Z"
  },
  {
    "id": "63f983d3-c59f-47f7-91c1-733a71955364",
    "legalName": "Slotix Browser QA qamvin5jg7 S.A.S.",
    "displayName": "Slotix Browser QA qamvin5jg7",
    "slug": "browser-qamvin5jg7",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:45:44.002095Z"
  },
  {
    "id": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "legalName": "Slotix Browser QA qamvin6yii S.A.S.",
    "displayName": "Slotix Browser QA qamvin6yii",
    "slug": "browser-qamvin6yii",
    "status": "ACTIVE",
    "timezone": "America/Bogota",
    "currencyCode": "COP",
    "contactEmail": "<redacted>",
    "createdAt": "2026-10-21T21:46:50.269541Z"
  }
]
```

## 005 — GET /api/companies

```bash
curl -i -X GET 'http://localhost:8080/api/companies'
```

**Observed response:** `HTTP 401`

```json
{
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "error": "Authentication is required or the token is invalid or expired",
  "fields": {}
}
```

## 006 — POST /api/users/login

```bash
curl -i -X POST 'http://localhost:8080/api/users/login' -H 'Content-Type: application/json' -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","password":"<redacted>"}'
```

**Observed response:** `HTTP 401`

```json
{
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "error": "Invalid email or password",
  "fields": {}
}
```

## 007 — POST /api/users/login

```bash
curl -i -X POST 'http://localhost:8080/api/users/login' -H 'Content-Type: application/json' -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","password":"<redacted>"}'
```

**Observed response:** `HTTP 200`

```json
{
  "token": "<redacted>",
  "tokenType": "<redacted>"
}
```

## 008 — GET /api/v1/bookings?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 009 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 010 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20'
```

**Observed response:** `HTTP 200`

```json
{
  "items": [
    {
      "id": "6856e395-9f6a-4c1b-96db-1eb2c4cedb33",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
      "email": "<redacted>",
      "fullName": "Browser QA Administrator",
      "status": "ACTIVE",
      "roles": [
        "COMPANY_ADMIN"
      ],
      "joinedAt": "2026-10-21T21:46:50.369273Z",
      "createdAt": "2026-10-21T21:46:50.369273Z",
      "updatedAt": "2026-10-21T21:46:50.369273Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 011 — POST /api/users

```bash
curl -i -X POST 'http://localhost:8080/api/users' -H 'Content-Type: application/json' -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","fullName":"Browser QA Customer","password":"<redacted>","roles":["CUSTOMER"]}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "email": "<redacted>",
  "fullName": "Browser QA Customer",
  "status": "ACTIVE",
  "createdAt": "2026-10-21T21:46:51.433693Z",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "roles": [
    "CUSTOMER"
  ]
}
```

## 012 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20'
```

**Observed response:** `HTTP 200`

```json
{
  "items": [
    {
      "id": "6856e395-9f6a-4c1b-96db-1eb2c4cedb33",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
      "email": "<redacted>",
      "fullName": "Browser QA Administrator",
      "status": "ACTIVE",
      "roles": [
        "COMPANY_ADMIN"
      ],
      "joinedAt": "2026-10-21T21:46:50.369273Z",
      "createdAt": "2026-10-21T21:46:50.369273Z",
      "updatedAt": "2026-10-21T21:46:50.369273Z"
    },
    {
      "id": "9724a166-fdf6-4596-badc-b5ce9255ec24",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
      "email": "<redacted>",
      "fullName": "Browser QA Customer",
      "status": "ACTIVE",
      "roles": [
        "CUSTOMER"
      ],
      "joinedAt": "2026-10-21T21:46:51.435359Z",
      "createdAt": "2026-10-21T21:46:51.435359Z",
      "updatedAt": "2026-10-21T21:46:51.435359Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

## 013 — POST /api/users

```bash
curl -i -X POST 'http://localhost:8080/api/users' -H 'Content-Type: application/json' -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","fullName":"Browser QA Manager","password":"<redacted>","roles":["BOOKING_MANAGER"]}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "f08ae139-ec81-49ab-a758-a92bf889bdf7",
  "email": "<redacted>",
  "fullName": "Browser QA Manager",
  "status": "ACTIVE",
  "createdAt": "2026-10-21T21:46:51.704439Z",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "roles": [
    "BOOKING_MANAGER"
  ]
}
```

## 014 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20'
```

**Observed response:** `HTTP 200`

```json
{
  "items": [
    {
      "id": "6856e395-9f6a-4c1b-96db-1eb2c4cedb33",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
      "email": "<redacted>",
      "fullName": "Browser QA Administrator",
      "status": "ACTIVE",
      "roles": [
        "COMPANY_ADMIN"
      ],
      "joinedAt": "2026-10-21T21:46:50.369273Z",
      "createdAt": "2026-10-21T21:46:50.369273Z",
      "updatedAt": "2026-10-21T21:46:50.369273Z"
    },
    {
      "id": "9724a166-fdf6-4596-badc-b5ce9255ec24",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
      "email": "<redacted>",
      "fullName": "Browser QA Customer",
      "status": "ACTIVE",
      "roles": [
        "CUSTOMER"
      ],
      "joinedAt": "2026-10-21T21:46:51.435359Z",
      "createdAt": "2026-10-21T21:46:51.435359Z",
      "updatedAt": "2026-10-21T21:46:51.435359Z"
    },
    {
      "id": "94299b2f-933a-41f7-a406-d54d7247d50a",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "f08ae139-ec81-49ab-a758-a92bf889bdf7",
      "email": "<redacted>",
      "fullName": "Browser QA Manager",
      "status": "ACTIVE",
      "roles": [
        "BOOKING_MANAGER"
      ],
      "joinedAt": "2026-10-21T21:46:51.707932Z",
      "createdAt": "2026-10-21T21:46:51.707932Z",
      "updatedAt": "2026-10-21T21:46:51.707932Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

## 015 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships/94299b2f-933a-41f7-a406-d54d7247d50a/suspend

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships/94299b2f-933a-41f7-a406-d54d7247d50a/suspend' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "94299b2f-933a-41f7-a406-d54d7247d50a",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "userId": "f08ae139-ec81-49ab-a758-a92bf889bdf7",
  "email": "<redacted>",
  "fullName": "Browser QA Manager",
  "status": "SUSPENDED",
  "roles": [
    "BOOKING_MANAGER"
  ],
  "joinedAt": "2026-10-21T21:46:51.707932Z",
  "createdAt": "2026-10-21T21:46:51.707932Z",
  "updatedAt": "2026-10-21T21:46:51.888568Z"
}
```

## 016 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20'
```

**Observed response:** `HTTP 200`

```json
{
  "items": [
    {
      "id": "6856e395-9f6a-4c1b-96db-1eb2c4cedb33",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
      "email": "<redacted>",
      "fullName": "Browser QA Administrator",
      "status": "ACTIVE",
      "roles": [
        "COMPANY_ADMIN"
      ],
      "joinedAt": "2026-10-21T21:46:50.369273Z",
      "createdAt": "2026-10-21T21:46:50.369273Z",
      "updatedAt": "2026-10-21T21:46:50.369273Z"
    },
    {
      "id": "9724a166-fdf6-4596-badc-b5ce9255ec24",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
      "email": "<redacted>",
      "fullName": "Browser QA Customer",
      "status": "ACTIVE",
      "roles": [
        "CUSTOMER"
      ],
      "joinedAt": "2026-10-21T21:46:51.435359Z",
      "createdAt": "2026-10-21T21:46:51.435359Z",
      "updatedAt": "2026-10-21T21:46:51.435359Z"
    },
    {
      "id": "94299b2f-933a-41f7-a406-d54d7247d50a",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "f08ae139-ec81-49ab-a758-a92bf889bdf7",
      "email": "<redacted>",
      "fullName": "Browser QA Manager",
      "status": "SUSPENDED",
      "roles": [
        "BOOKING_MANAGER"
      ],
      "joinedAt": "2026-10-21T21:46:51.707932Z",
      "createdAt": "2026-10-21T21:46:51.707932Z",
      "updatedAt": "2026-10-21T21:46:51.888568Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

## 017 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships/94299b2f-933a-41f7-a406-d54d7247d50a/activate

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships/94299b2f-933a-41f7-a406-d54d7247d50a/activate' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "94299b2f-933a-41f7-a406-d54d7247d50a",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "userId": "f08ae139-ec81-49ab-a758-a92bf889bdf7",
  "email": "<redacted>",
  "fullName": "Browser QA Manager",
  "status": "ACTIVE",
  "roles": [
    "BOOKING_MANAGER"
  ],
  "joinedAt": "2026-10-21T21:46:51.707932Z",
  "createdAt": "2026-10-21T21:46:51.707932Z",
  "updatedAt": "2026-10-21T21:46:52.017247Z"
}
```

## 018 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/memberships?page=0&size=20'
```

**Observed response:** `HTTP 200`

```json
{
  "items": [
    {
      "id": "6856e395-9f6a-4c1b-96db-1eb2c4cedb33",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
      "email": "<redacted>",
      "fullName": "Browser QA Administrator",
      "status": "ACTIVE",
      "roles": [
        "COMPANY_ADMIN"
      ],
      "joinedAt": "2026-10-21T21:46:50.369273Z",
      "createdAt": "2026-10-21T21:46:50.369273Z",
      "updatedAt": "2026-10-21T21:46:50.369273Z"
    },
    {
      "id": "9724a166-fdf6-4596-badc-b5ce9255ec24",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
      "email": "<redacted>",
      "fullName": "Browser QA Customer",
      "status": "ACTIVE",
      "roles": [
        "CUSTOMER"
      ],
      "joinedAt": "2026-10-21T21:46:51.435359Z",
      "createdAt": "2026-10-21T21:46:51.435359Z",
      "updatedAt": "2026-10-21T21:46:51.435359Z"
    },
    {
      "id": "94299b2f-933a-41f7-a406-d54d7247d50a",
      "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
      "userId": "f08ae139-ec81-49ab-a758-a92bf889bdf7",
      "email": "<redacted>",
      "fullName": "Browser QA Manager",
      "status": "ACTIVE",
      "roles": [
        "BOOKING_MANAGER"
      ],
      "joinedAt": "2026-10-21T21:46:51.707932Z",
      "createdAt": "2026-10-21T21:46:51.707932Z",
      "updatedAt": "2026-10-21T21:46:52.017247Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1
}
```

## 019 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 020 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources' -H 'Content-Type: application/json' -d '{"name":"Sala automática qamvin6yii","description":"QA resource created from browser qamvin6yii","capacity":1,"resourceType":"SPACE","visibility":"MEMBERS"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "name": "Sala automática qamvin6yii",
  "description": "QA resource created from browser qamvin6yii",
  "resourceType": "SPACE",
  "capacity": 1,
  "status": "DRAFT",
  "visibility": "MEMBERS",
  "createdAt": "2026-10-21T21:46:52.350529Z",
  "updatedAt": "2026-10-21T21:46:52.350529Z"
}
```

## 021 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "DRAFT",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.350529Z"
  }
]
```

## 022 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 023 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 024 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 025 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/blocks'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 026 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/activate

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/activate' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "name": "Sala automática qamvin6yii",
  "description": "QA resource created from browser qamvin6yii",
  "resourceType": "SPACE",
  "capacity": 1,
  "status": "ACTIVE",
  "visibility": "MEMBERS",
  "createdAt": "2026-10-21T21:46:52.350529Z",
  "updatedAt": "2026-10-21T21:46:52.500491Z"
}
```

## 027 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules' -H 'Content-Type: application/json' -d '{"weekday":4,"startLocalTime":"08:00","endLocalTime":"18:00"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "53ce1436-5553-4b06-9815-cc9b6ade9e58",
  "weekday": 4,
  "startLocalTime": "08:00:00",
  "endLocalTime": "18:00:00",
  "effectiveFrom": null,
  "effectiveTo": null
}
```

## 028 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/blocks'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 029 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "53ce1436-5553-4b06-9815-cc9b6ade9e58",
    "weekday": 4,
    "startLocalTime": "08:00:00",
    "endLocalTime": "18:00:00",
    "effectiveFrom": null,
    "effectiveTo": null
  }
]
```

## 030 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 031 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources' -H 'Content-Type: application/json' -d '{"name":"Sala aprobaciones qamvin6yii","description":"QA resource created from browser qamvin6yii","capacity":1,"resourceType":"SPACE","visibility":"MEMBERS"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "name": "Sala aprobaciones qamvin6yii",
  "description": "QA resource created from browser qamvin6yii",
  "resourceType": "SPACE",
  "capacity": 1,
  "status": "DRAFT",
  "visibility": "MEMBERS",
  "createdAt": "2026-10-21T21:46:52.981374Z",
  "updatedAt": "2026-10-21T21:46:52.981374Z"
}
```

## 032 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "DRAFT",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:52.981374Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 033 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 034 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 035 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 036 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 037 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/activate

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/activate' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "name": "Sala aprobaciones qamvin6yii",
  "description": "QA resource created from browser qamvin6yii",
  "resourceType": "SPACE",
  "capacity": 1,
  "status": "ACTIVE",
  "visibility": "MEMBERS",
  "createdAt": "2026-10-21T21:46:52.981374Z",
  "updatedAt": "2026-10-21T21:46:53.131639Z"
}
```

## 038 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules' -H 'Content-Type: application/json' -d '{"weekday":4,"startLocalTime":"08:00","endLocalTime":"18:00"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "75f2849c-ccc8-4cfc-82ad-5941c4a99c76",
  "weekday": 4,
  "startLocalTime": "08:00:00",
  "endLocalTime": "18:00:00",
  "effectiveFrom": null,
  "effectiveTo": null
}
```

## 039 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 040 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "75f2849c-ccc8-4cfc-82ad-5941c4a99c76",
    "weekday": 4,
    "startLocalTime": "08:00:00",
    "endLocalTime": "18:00:00",
    "effectiveFrom": null,
    "effectiveTo": null
  }
]
```

## 041 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 042 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies' -H 'Content-Type: application/json' -d '{"name":"Automática qamvin6yii","minDurationMinutes":30,"maxDurationMinutes":120,"slotIncrementMinutes":30,"minNoticeMinutes":0,"maxAdvanceDays":31,"cancellationNoticeMinutes":0,"approvalRequired":false,"allowCustomerCancel":true}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2"
}
```

## 043 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Automática qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": false,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.560872Z",
    "updatedAt": "2026-10-21T21:46:53.560872Z"
  }
]
```

## 044 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies' -H 'Content-Type: application/json' -d '{"name":"Con aprobación qamvin6yii","minDurationMinutes":30,"maxDurationMinutes":120,"slotIncrementMinutes":30,"minNoticeMinutes":0,"maxAdvanceDays":31,"cancellationNoticeMinutes":0,"approvalRequired":true,"allowCustomerCancel":true}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916"
}
```

## 045 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Automática qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": false,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.560872Z",
    "updatedAt": "2026-10-21T21:46:53.560872Z"
  },
  {
    "id": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Con aprobación qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": true,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.884365Z",
    "updatedAt": "2026-10-21T21:46:53.884365Z"
  }
]
```

## 046 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 047 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 048 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Automática qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": false,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.560872Z",
    "updatedAt": "2026-10-21T21:46:53.560872Z"
  },
  {
    "id": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Con aprobación qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": true,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.884365Z",
    "updatedAt": "2026-10-21T21:46:53.884365Z"
  }
]
```

## 049 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability-rules'
```

**Observed response:** `HTTP 200`

```json
"<unavailable>"
```

## 050 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 051 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/blocks'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 052 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies' -H 'Content-Type: application/json' -d '{"policyId":"c91f53ef-f554-4e3c-a624-600a5c6b7fd2","effectiveFrom":"2026-10-21T05:00:00.000Z","effectiveTo":null}'
```

**Observed response:** `HTTP 201`

```json
{
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "policyId": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
  "effectiveFrom": "2026-10-21T05:00:00Z",
  "effectiveTo": null
}
```

## 053 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Automática qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": false,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.560872Z",
    "updatedAt": "2026-10-21T21:46:53.560872Z"
  },
  {
    "id": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Con aprobación qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": true,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.884365Z",
    "updatedAt": "2026-10-21T21:46:53.884365Z"
  }
]
```

## 054 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "policyId": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "effectiveFrom": "2026-10-21T05:00:00Z",
    "effectiveTo": null
  }
]
```

## 055 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 056 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 057 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Automática qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": false,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.560872Z",
    "updatedAt": "2026-10-21T21:46:53.560872Z"
  },
  {
    "id": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Con aprobación qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": true,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.884365Z",
    "updatedAt": "2026-10-21T21:46:53.884365Z"
  }
]
```

## 058 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 059 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 060 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "75f2849c-ccc8-4cfc-82ad-5941c4a99c76",
    "weekday": 4,
    "startLocalTime": "08:00:00",
    "endLocalTime": "18:00:00",
    "effectiveFrom": null,
    "effectiveTo": null
  }
]
```

## 061 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies' -H 'Content-Type: application/json' -d '{"policyId":"d3e0913e-68e5-4fc6-bc73-fe2ac538f916","effectiveFrom":"2026-10-21T05:00:00.000Z","effectiveTo":null}'
```

**Observed response:** `HTTP 201`

```json
{
  "resourceId": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
  "policyId": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
  "effectiveFrom": "2026-10-21T05:00:00Z",
  "effectiveTo": null
}
```

## 062 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/booking-policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "c91f53ef-f554-4e3c-a624-600a5c6b7fd2",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Automática qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": false,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.560872Z",
    "updatedAt": "2026-10-21T21:46:53.560872Z"
  },
  {
    "id": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Con aprobación qamvin6yii",
    "minDurationMinutes": 30,
    "maxDurationMinutes": 120,
    "slotIncrementMinutes": 30,
    "minNoticeMinutes": 0,
    "maxAdvanceDays": 31,
    "cancellationNoticeMinutes": 0,
    "approvalRequired": true,
    "allowCustomerCancel": true,
    "status": "ACTIVE",
    "createdAt": "2026-10-21T21:46:53.884365Z",
    "updatedAt": "2026-10-21T21:46:53.884365Z"
  }
]
```

## 063 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/policies'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "resourceId": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "policyId": "d3e0913e-68e5-4fc6-bc73-fe2ac538f916",
    "effectiveFrom": "2026-10-21T05:00:00Z",
    "effectiveTo": null
  }
]
```

## 064 — POST /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks

```bash
curl -i -X POST 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks' -H 'Content-Type: application/json' -d '{"startAt":"2026-10-22T17:00:00.000Z","endAt":"2026-10-22T18:00:00.000Z","reason":"Browser QA maintenance block","blockType":"MAINTENANCE"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "69069f2c-6ed4-4c24-a7c4-3f41ff80b1c8",
  "startAt": "2026-10-22T17:00:00Z",
  "endAt": "2026-10-22T18:00:00Z",
  "reason": "Browser QA maintenance block",
  "blockType": "MAINTENANCE"
}
```

## 065 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/blocks'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "69069f2c-6ed4-4c24-a7c4-3f41ff80b1c8",
    "startAt": "2026-10-22T17:00:00Z",
    "endAt": "2026-10-22T18:00:00Z",
    "reason": "Browser QA maintenance block",
    "blockType": "MAINTENANCE"
  }
]
```

## 066 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability-rules'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "75f2849c-ccc8-4cfc-82ad-5941c4a99c76",
    "weekday": 4,
    "startLocalTime": "08:00:00",
    "endLocalTime": "18:00:00",
    "effectiveFrom": null,
    "effectiveTo": null
  }
]
```

## 067 — POST /api/users/login

```bash
curl -i -X POST 'http://localhost:8080/api/users/login' -H 'Content-Type: application/json' -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","password":"<redacted>"}'
```

**Observed response:** `HTTP 200`

```json
{
  "token": "<redacted>",
  "tokenType": "<redacted>"
}
```

## 068 — GET /api/v1/bookings?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 069 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 070 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 071 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=45

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=45'
```

**Observed response:** `HTTP 200`

```json
[]
```

## 072 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=30

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=30'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z"
  },
  {
    "startAt": "2026-10-22T13:30:00Z",
    "endAt": "2026-10-22T14:00:00Z"
  },
  {
    "startAt": "2026-10-22T14:00:00Z",
    "endAt": "2026-10-22T14:30:00Z"
  },
  {
    "startAt": "2026-10-22T14:30:00Z",
    "endAt": "2026-10-22T15:00:00Z"
  },
  {
    "startAt": "2026-10-22T15:00:00Z",
    "endAt": "2026-10-22T15:30:00Z"
  },
  {
    "startAt": "2026-10-22T15:30:00Z",
    "endAt": "2026-10-22T16:00:00Z"
  },
  {
    "startAt": "2026-10-22T16:00:00Z",
    "endAt": "2026-10-22T16:30:00Z"
  },
  {
    "startAt": "2026-10-22T16:30:00Z",
    "endAt": "2026-10-22T17:00:00Z"
  },
  {
    "startAt": "2026-10-22T17:00:00Z",
    "endAt": "2026-10-22T17:30:00Z"
  },
  {
    "startAt": "2026-10-22T17:30:00Z",
    "endAt": "2026-10-22T18:00:00Z"
  },
  {
    "startAt": "2026-10-22T18:00:00Z",
    "endAt": "2026-10-22T18:30:00Z"
  },
  {
    "startAt": "2026-10-22T18:30:00Z",
    "endAt": "2026-10-22T19:00:00Z"
  },
  {
    "startAt": "2026-10-22T19:00:00Z",
    "endAt": "2026-10-22T19:30:00Z"
  },
  {
    "startAt": "2026-10-22T19:30:00Z",
    "endAt": "2026-10-22T20:00:00Z"
  },
  {
    "startAt": "2026-10-22T20:00:00Z",
    "endAt": "2026-10-22T20:30:00Z"
  },
  {
    "startAt": "2026-10-22T20:30:00Z",
    "endAt": "2026-10-22T21:00:00Z"
  },
  {
    "startAt": "2026-10-22T21:00:00Z",
    "endAt": "2026-10-22T21:30:00Z"
  },
  {
    "startAt": "2026-10-22T21:30:00Z",
    "endAt": "2026-10-22T22:00:00Z"
  },
  {
    "startAt": "2026-10-22T22:00:00Z",
    "endAt": "2026-10-22T22:30:00Z"
  },
  {
    "startAt": "2026-10-22T22:30:00Z",
    "endAt": "2026-10-22T23:00:00Z"
  }
]
```

## 073 — POST /api/v1/bookings

```bash
curl -i -X POST 'http://localhost:8080/api/v1/bookings' -H 'Content-Type: application/json' -d '{"resourceId":"e815de09-09ef-4f80-900f-3ef76cd91847","startAt":"2026-10-22T13:00:00Z","endAt":"2026-10-22T13:30:00Z"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "ebbfba31-100a-4def-98e5-b385e680da08",
  "bookingNumber": 4,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CONFIRMED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 074 — GET /api/v1/bookings/ebbfba31-100a-4def-98e5-b385e680da08

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings/ebbfba31-100a-4def-98e5-b385e680da08'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "ebbfba31-100a-4def-98e5-b385e680da08",
  "bookingNumber": 4,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CONFIRMED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 075 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 076 — POST /api/v1/bookings/ebbfba31-100a-4def-98e5-b385e680da08/cancel

```bash
curl -i -X POST 'http://localhost:8080/api/v1/bookings/ebbfba31-100a-4def-98e5-b385e680da08/cancel' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "ebbfba31-100a-4def-98e5-b385e680da08",
  "bookingNumber": 4,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CANCELLED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": "2026-10-21T21:46:56.501615Z",
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 077 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 078 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=30

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/e815de09-09ef-4f80-900f-3ef76cd91847/availability?date=2026-10-22&durationMinutes=30'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z"
  },
  {
    "startAt": "2026-10-22T13:30:00Z",
    "endAt": "2026-10-22T14:00:00Z"
  },
  {
    "startAt": "2026-10-22T14:00:00Z",
    "endAt": "2026-10-22T14:30:00Z"
  },
  {
    "startAt": "2026-10-22T14:30:00Z",
    "endAt": "2026-10-22T15:00:00Z"
  },
  {
    "startAt": "2026-10-22T15:00:00Z",
    "endAt": "2026-10-22T15:30:00Z"
  },
  {
    "startAt": "2026-10-22T15:30:00Z",
    "endAt": "2026-10-22T16:00:00Z"
  },
  {
    "startAt": "2026-10-22T16:00:00Z",
    "endAt": "2026-10-22T16:30:00Z"
  },
  {
    "startAt": "2026-10-22T16:30:00Z",
    "endAt": "2026-10-22T17:00:00Z"
  },
  {
    "startAt": "2026-10-22T17:00:00Z",
    "endAt": "2026-10-22T17:30:00Z"
  },
  {
    "startAt": "2026-10-22T17:30:00Z",
    "endAt": "2026-10-22T18:00:00Z"
  },
  {
    "startAt": "2026-10-22T18:00:00Z",
    "endAt": "2026-10-22T18:30:00Z"
  },
  {
    "startAt": "2026-10-22T18:30:00Z",
    "endAt": "2026-10-22T19:00:00Z"
  },
  {
    "startAt": "2026-10-22T19:00:00Z",
    "endAt": "2026-10-22T19:30:00Z"
  },
  {
    "startAt": "2026-10-22T19:30:00Z",
    "endAt": "2026-10-22T20:00:00Z"
  },
  {
    "startAt": "2026-10-22T20:00:00Z",
    "endAt": "2026-10-22T20:30:00Z"
  },
  {
    "startAt": "2026-10-22T20:30:00Z",
    "endAt": "2026-10-22T21:00:00Z"
  },
  {
    "startAt": "2026-10-22T21:00:00Z",
    "endAt": "2026-10-22T21:30:00Z"
  },
  {
    "startAt": "2026-10-22T21:30:00Z",
    "endAt": "2026-10-22T22:00:00Z"
  },
  {
    "startAt": "2026-10-22T22:00:00Z",
    "endAt": "2026-10-22T22:30:00Z"
  },
  {
    "startAt": "2026-10-22T22:30:00Z",
    "endAt": "2026-10-22T23:00:00Z"
  }
]
```

## 079 — POST /api/v1/bookings

```bash
curl -i -X POST 'http://localhost:8080/api/v1/bookings' -H 'Content-Type: application/json' -d '{"resourceId":"e815de09-09ef-4f80-900f-3ef76cd91847","startAt":"2026-10-22T13:00:00Z","endAt":"2026-10-22T13:30:00Z"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "276f2b93-c46e-4fe9-b126-f01004a218b8",
  "bookingNumber": 5,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CONFIRMED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 080 — GET /api/v1/bookings/276f2b93-c46e-4fe9-b126-f01004a218b8

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings/276f2b93-c46e-4fe9-b126-f01004a218b8'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "276f2b93-c46e-4fe9-b126-f01004a218b8",
  "bookingNumber": 5,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CONFIRMED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 081 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 082 — GET /api/v1/calendar?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z

```bash
curl -i -X GET 'http://localhost:8080/api/v1/calendar?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "276f2b93-c46e-4fe9-b126-f01004a218b8",
    "bookingNumber": 5,
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z",
    "status": "CONFIRMED",
    "timezone": "America/Bogota",
    "notes": null,
    "cancelledAt": null,
    "cancellationReason": null,
    "checkedInAt": null,
    "checkedInBy": null,
    "completedAt": null,
    "completedBy": null,
    "noShowAt": null,
    "noShowBy": null
  },
  {
    "id": "ebbfba31-100a-4def-98e5-b385e680da08",
    "bookingNumber": 4,
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z",
    "status": "CANCELLED",
    "timezone": "America/Bogota",
    "notes": null,
    "cancelledAt": "2026-10-21T21:46:56.501615Z",
    "cancellationReason": null,
    "checkedInAt": null,
    "checkedInBy": null,
    "completedAt": null,
    "completedBy": null,
    "noShowAt": null,
    "noShowBy": null
  }
]
```

## 083 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 084 — POST /api/users/login

```bash
curl -i -X POST 'http://localhost:8080/api/users/login' -H 'Content-Type: application/json' -d '{"companyId":"3b990fb0-3b30-48e3-b22a-75469ec99059","email":"<redacted>","password":"<redacted>"}'
```

**Observed response:** `HTTP 200`

```json
{
  "token": "<redacted>",
  "tokenType": "<redacted>"
}
```

## 085 — GET /api/v1/bookings?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings?from=2026-10-21T05:00:00.000Z&to=2026-10-28T05:00:00.000Z'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "276f2b93-c46e-4fe9-b126-f01004a218b8",
    "bookingNumber": 5,
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z",
    "status": "CONFIRMED",
    "timezone": "America/Bogota",
    "notes": null,
    "cancelledAt": null,
    "cancellationReason": null,
    "checkedInAt": null,
    "checkedInBy": null,
    "completedAt": null,
    "completedBy": null,
    "noShowAt": null,
    "noShowBy": null
  },
  {
    "id": "ebbfba31-100a-4def-98e5-b385e680da08",
    "bookingNumber": 4,
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z",
    "status": "CANCELLED",
    "timezone": "America/Bogota",
    "notes": null,
    "cancelledAt": "2026-10-21T21:46:56.501615Z",
    "cancellationReason": null,
    "checkedInAt": null,
    "checkedInBy": null,
    "completedAt": null,
    "completedBy": null,
    "noShowAt": null,
    "noShowBy": null
  }
]
```

## 086 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 087 — GET /api/v1/bookings/276f2b93-c46e-4fe9-b126-f01004a218b8

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings/276f2b93-c46e-4fe9-b126-f01004a218b8'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "276f2b93-c46e-4fe9-b126-f01004a218b8",
  "bookingNumber": 5,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "e815de09-09ef-4f80-900f-3ef76cd91847",
  "customerUserId": "fb9e0874-1742-4e19-973e-c9ad6ad96d8f",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CONFIRMED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 088 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 089 — POST /api/v1/bookings/276f2b93-c46e-4fe9-b126-f01004a218b8/check-in

```bash
curl -i -X POST 'http://localhost:8080/api/v1/bookings/276f2b93-c46e-4fe9-b126-f01004a218b8/check-in' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 422`

```json
{
  "status": 422,
  "code": "BOOKING_STATE_INVALID",
  "error": "Check-in must occur within the booking interval",
  "fields": {}
}
```

## 090 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 091 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability?date=2026-10-22&durationMinutes=30

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources/b5ae59d3-8e10-426b-b46f-5122833b4cac/availability?date=2026-10-22&durationMinutes=30'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "startAt": "2026-10-22T13:00:00Z",
    "endAt": "2026-10-22T13:30:00Z"
  },
  {
    "startAt": "2026-10-22T13:30:00Z",
    "endAt": "2026-10-22T14:00:00Z"
  },
  {
    "startAt": "2026-10-22T14:00:00Z",
    "endAt": "2026-10-22T14:30:00Z"
  },
  {
    "startAt": "2026-10-22T14:30:00Z",
    "endAt": "2026-10-22T15:00:00Z"
  },
  {
    "startAt": "2026-10-22T15:00:00Z",
    "endAt": "2026-10-22T15:30:00Z"
  },
  {
    "startAt": "2026-10-22T15:30:00Z",
    "endAt": "2026-10-22T16:00:00Z"
  },
  {
    "startAt": "2026-10-22T16:00:00Z",
    "endAt": "2026-10-22T16:30:00Z"
  },
  {
    "startAt": "2026-10-22T16:30:00Z",
    "endAt": "2026-10-22T17:00:00Z"
  },
  {
    "startAt": "2026-10-22T18:00:00Z",
    "endAt": "2026-10-22T18:30:00Z"
  },
  {
    "startAt": "2026-10-22T18:30:00Z",
    "endAt": "2026-10-22T19:00:00Z"
  },
  {
    "startAt": "2026-10-22T19:00:00Z",
    "endAt": "2026-10-22T19:30:00Z"
  },
  {
    "startAt": "2026-10-22T19:30:00Z",
    "endAt": "2026-10-22T20:00:00Z"
  },
  {
    "startAt": "2026-10-22T20:00:00Z",
    "endAt": "2026-10-22T20:30:00Z"
  },
  {
    "startAt": "2026-10-22T20:30:00Z",
    "endAt": "2026-10-22T21:00:00Z"
  },
  {
    "startAt": "2026-10-22T21:00:00Z",
    "endAt": "2026-10-22T21:30:00Z"
  },
  {
    "startAt": "2026-10-22T21:30:00Z",
    "endAt": "2026-10-22T22:00:00Z"
  },
  {
    "startAt": "2026-10-22T22:00:00Z",
    "endAt": "2026-10-22T22:30:00Z"
  },
  {
    "startAt": "2026-10-22T22:30:00Z",
    "endAt": "2026-10-22T23:00:00Z"
  }
]
```

## 092 — POST /api/v1/bookings

```bash
curl -i -X POST 'http://localhost:8080/api/v1/bookings' -H 'Content-Type: application/json' -d '{"resourceId":"b5ae59d3-8e10-426b-b46f-5122833b4cac","startAt":"2026-10-22T13:00:00Z","endAt":"2026-10-22T13:30:00Z"}'
```

**Observed response:** `HTTP 201`

```json
{
  "id": "634b2095-8ce2-4447-8510-0fa38d2ce10e",
  "bookingNumber": 6,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
  "customerUserId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "PENDING",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 093 — GET /api/v1/bookings/634b2095-8ce2-4447-8510-0fa38d2ce10e

```bash
curl -i -X GET 'http://localhost:8080/api/v1/bookings/634b2095-8ce2-4447-8510-0fa38d2ce10e'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "634b2095-8ce2-4447-8510-0fa38d2ce10e",
  "bookingNumber": 6,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
  "customerUserId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "PENDING",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```

## 094 — GET /api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources

```bash
curl -i -X GET 'http://localhost:8080/api/v1/companies/3b990fb0-3b30-48e3-b22a-75469ec99059/resources'
```

**Observed response:** `HTTP 200`

```json
[
  {
    "id": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala aprobaciones qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.981374Z",
    "updatedAt": "2026-10-21T21:46:53.131639Z"
  },
  {
    "id": "e815de09-09ef-4f80-900f-3ef76cd91847",
    "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
    "name": "Sala automática qamvin6yii",
    "description": "QA resource created from browser qamvin6yii",
    "resourceType": "SPACE",
    "capacity": 1,
    "status": "ACTIVE",
    "visibility": "MEMBERS",
    "createdAt": "2026-10-21T21:46:52.350529Z",
    "updatedAt": "2026-10-21T21:46:52.500491Z"
  }
]
```

## 095 — POST /api/v1/bookings/634b2095-8ce2-4447-8510-0fa38d2ce10e/approve

```bash
curl -i -X POST 'http://localhost:8080/api/v1/bookings/634b2095-8ce2-4447-8510-0fa38d2ce10e/approve' -H 'Content-Type: application/json' -d '{}'
```

**Observed response:** `HTTP 200`

```json
{
  "id": "634b2095-8ce2-4447-8510-0fa38d2ce10e",
  "bookingNumber": 6,
  "companyId": "3b990fb0-3b30-48e3-b22a-75469ec99059",
  "resourceId": "b5ae59d3-8e10-426b-b46f-5122833b4cac",
  "customerUserId": "6419b6c3-c776-4627-b630-25a1b2b6e0a0",
  "startAt": "2026-10-22T13:00:00Z",
  "endAt": "2026-10-22T13:30:00Z",
  "status": "CONFIRMED",
  "timezone": "America/Bogota",
  "notes": null,
  "cancelledAt": null,
  "cancellationReason": null,
  "checkedInAt": null,
  "checkedInBy": null,
  "completedAt": null,
  "completedBy": null,
  "noShowAt": null,
  "noShowBy": null
}
```