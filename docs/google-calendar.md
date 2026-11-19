# Google Calendar connection

This first integration creates a dedicated secondary Google calendar and lets company administrators export or update a booking from its detail page. Slotix remains the source of truth. Synchronization is manual and one-way; external edits do not change availability. Automatic synchronization, external busy-time import and Outlook remain pending.

## Configuration

Create a Google Cloud OAuth client of type Web application, enable Calendar API and configure the consent screen and test users. Set these server environment variables:

```sh
APP_GOOGLE_CALENDAR_CLIENT_ID=...
APP_GOOGLE_CALENDAR_CLIENT_SECRET=...
APP_GOOGLE_CALENDAR_REDIRECT_URI=http://localhost:4200/integrations/google/callback
APP_GOOGLE_CALENDAR_ENCRYPTION_KEY=...
```

The encryption key must be a separately generated Base64-encoded 32-byte secret. Store it in your deployment's secret manager and preserve it across restarts. Use HTTPS in production. The exact frontend callback URI must be registered in Google. Avoid logging callback query strings, authorization codes, refresh tokens or client secrets. Do not put any of these secrets in frontend runtime configuration or source control.

The only requested Google scope is `https://www.googleapis.com/auth/calendar.app.created`, permitting calendars created by this application. No access to the user's personal calendar is requested. Exported events contain only resource name, booking number and time range; customer names, email addresses and notes are excluded. Events use private visibility and disable Google's default reminders to avoid duplicate reminders.

OAuth initiation and completion require a current company administrator JWT. State is single-use, hashed in PostgreSQL, bound to user and company, expires after ten minutes and is paired with PKCE S256. The verifier and refresh token use AES-GCM with company-specific authenticated context. Provider endpoints are fixed and redirects are disabled; requests have timeouts. Authorization and export are rate limited. The browser checks callback origin and popup identity and clears callback parameters from browser history.

## Routes

- `GET /api/v1/companies/{companyId}/google-calendar`: configuration/connection status only.
- `POST .../connect`: authorization URL; no client secret or provider token is returned.
- `POST .../complete`: exchange the one-time authorization code and create the secondary calendar.
- `POST .../bookings/{bookingId}`: export the current booking using a deterministic event ID. Repeat exports update the same event. Cancelled/rejected/expired/no-show copies are cancelled; pending copies are tentative.
- `DELETE .../google-calendar`: delete locally stored credentials and pending state. It preserves the external calendar and events. Revoke the application's Google grant separately in Google account settings; revoking a grant can affect other connections made with the same Google account.

## Verification and limits

Tests exercise real PostgreSQL state, tenant/administrator checks, invalid state, restricted authorization URL and tenant-bound authenticated encryption. They do not contact Google. Actual OAuth authorization, event creation/update, provider revocation and cancelled-event behavior require Google credentials and a consenting test account and remain unverified. A provider failure consumes the OAuth state; restart the connection flow rather than replaying a code. Simultaneous separate connection attempts can leave an unused secondary calendar in Google; only one connection is retained locally. Events deleted manually in Google may require reconnecting to a fresh calendar if Google refuses to reuse the deterministic event ID.

Sources: [OAuth web server flow](https://developers.google.com/identity/protocols/oauth2/web-server), [Calendar scopes](https://developers.google.com/workspace/calendar/api/auth), [secondary calendar creation](https://developers.google.com/workspace/calendar/api/v3/reference/calendars/insert), [event insertion and IDs](https://developers.google.com/workspace/calendar/api/v3/reference/events/insert).
