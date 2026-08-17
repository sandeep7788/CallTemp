# Twilio Configuration

This application supports two Twilio calling paths:

1. **Browser-to-PSTN calls** through the Twilio Voice JavaScript SDK and a TwiML App.
2. **Server-initiated outbound calls** through the Twilio REST API and per-call dynamic TwiML URLs.

All production webhook URLs must be public HTTPS URLs. Twilio cannot call `localhost` or a private IP unless you expose it through a tunnel for development.

## Required Firestore `app_config` values

The application reads Twilio credentials from the Firestore `app_config` collection, not from `application.properties`.

| Key | Required | Description |
| --- | --- | --- |
| `twilio_account_sid` | Yes | Twilio Account SID, starts with `AC`. |
| `twilio_auth_token` | Yes | Twilio Auth Token. Used for REST API calls and optional webhook signature validation. |
| `twilio_app_sid` | Yes for browser calls | TwiML App SID, starts with `AP`. This is embedded in browser Voice access tokens. |
| `twilio_api_key` | Yes for browser calls | Twilio API Key SID, starts with `SK`. |
| `twilio_api_secret` | Yes for browser calls | Twilio API Key secret. Must be at least 32 bytes for JWT signing in this project. |
| `twilio_caller_id` | Optional fallback | Verified/purchased caller ID used only if the managed `twilio_numbers` pool has no available number. |
| `twilio_incoming_client_identity` | Optional inbound PSTN | Default Twilio Voice SDK client identity for routing inbound PSTN calls to a browser client, for example `client-abc12345`. |
| `outbound_call_twiml_ttl_minutes` | Optional | TTL for server-initiated outbound call TwiML URLs. Default: 15 minutes. |

The managed caller-ID pool is stored in Firestore collection `twilio_numbers`. Add active Twilio numbers through the existing `/api/twilio-numbers` API or by seeding Firestore with E.164 numbers such as `+14155552671`.

## Required environment variables

| Variable | Required | Description |
| --- | --- | --- |
| `APP_PUBLIC_BASE_URL` | Production: yes | Public HTTPS origin for webhook URLs, for example `https://makecall.in`. Must not include a trailing slash. |
| `TWILIO_VALIDATE_SIGNATURE` | Recommended after setup | Set to `true` only when `APP_PUBLIC_BASE_URL` exactly matches the URL Twilio uses. |
| `FIREBASE_CREDENTIALS_PATH` | Production: yes | Path to the Firebase Admin service account JSON. |
| `FIREBASE_PROJECT_ID` | Production: yes | Firebase project ID. |

`application.properties` defaults `app.public-base-url` to `https://makecall.in`; verify this matches the actual production domain before enabling webhooks.

## TwiML App configuration for browser calling

In Twilio Console:

1. Go to **Voice > TwiML Apps**.
2. Create or edit the TwiML App whose SID is stored in `twilio_app_sid`.
3. Configure:

| TwiML App field | Value |
| --- | --- |
| Voice Request URL | `https://YOUR_DOMAIN/call/voice` |
| Voice Request Method | `POST` |
| Status Callback URL | `https://YOUR_DOMAIN/call/status-callback` |
| Status Callback Method | `POST` |

The browser sends `DialedNumber` through `device.connect(...)`. `/call/voice` dynamically returns TwiML like:

```xml
<Response>
  <Dial callerId="+1..." timeLimit="..." answerOnBridge="true" action="https://YOUR_DOMAIN/call/dial-complete" method="POST">
    <Number statusCallback="https://YOUR_DOMAIN/call/status-callback" statusCallbackMethod="POST" statusCallbackEvent="initiated ringing answered completed">+91...</Number>
  </Dial>
</Response>
```

## Twilio phone number configuration

For each Twilio PSTN number used by the app:

### Outgoing caller ID

- The number must be a Twilio-owned number on the account, or a verified caller ID.
- Trial accounts may only call verified destination numbers.
- Production accounts should use purchased Twilio numbers that support Voice in the target region.

### Incoming PSTN calls

If you want inbound PSTN calls to ring a browser client, configure the Twilio number:

| Phone Number field | Value |
| --- | --- |
| A call comes in | Webhook |
| Voice Webhook URL | `https://YOUR_DOMAIN/call/direct-client?client=CLIENT_IDENTITY` |
| Method | `POST` |

Alternatively, omit the query parameter and set Firestore `app_config.twilio_incoming_client_identity` to the target client identity. The target browser must be logged in and registered with the Twilio Voice SDK using that identity.

## Server-initiated outbound calls

`POST /call/outbound` creates a Twilio REST API call from an allocated Twilio number to the destination. Twilio then requests:

| Purpose | URL pattern | Method |
| --- | --- | --- |
| Dynamic TwiML | `https://YOUR_DOMAIN/call/outbound/twiml/{publicId}` | `POST` |
| REST call status | `https://YOUR_DOMAIN/call/outbound/status/{publicId}` | `POST` |

These URLs are generated from `APP_PUBLIC_BASE_URL` or, if blank, forwarded request headers.

## HTTPS requirements

- Use a valid public TLS certificate.
- Configure the reverse proxy to preserve `X-Forwarded-Proto` and `X-Forwarded-Host` if `APP_PUBLIC_BASE_URL` is blank.
- Avoid redirects on webhook URLs. Twilio webhooks should return directly with HTTP 200 and XML for TwiML endpoints.
- Mobile browsers require HTTPS for microphone/WebRTC access except limited localhost development scenarios.

## Webhook signature validation

Set `TWILIO_VALIDATE_SIGNATURE=true` only after verifying:

1. `APP_PUBLIC_BASE_URL` exactly matches the public scheme, host, and path Twilio calls.
2. Reverse proxy redirects do not change the webhook URL.
3. `twilio_auth_token` is correct.

If any of those are wrong, Twilio requests will be rejected with HTTP 403.

## Common Twilio mistakes

- Pointing the TwiML App Voice URL to `/call/outbound/twiml` instead of `/call/voice` for browser calls.
- Returning JSON or HTML from a TwiML endpoint instead of XML.
- Missing `Content-Type: application/xml` on TwiML endpoints.
- Using an unverified caller ID or an inactive Twilio number.
- Using trial accounts to call unverified destination numbers.
- Setting `APP_PUBLIC_BASE_URL` to a private/local URL.
- Enabling signature validation before the public URL is final.
- Forgetting to add Twilio numbers to the Firestore `twilio_numbers` pool.
- Expecting Twilio inbound webhooks to have a browser HTTP session; Twilio webhooks are server-to-server and must route by query parameter or configured client identity.

## Production checklist

- [ ] `twilio_account_sid`, `twilio_auth_token`, `twilio_app_sid`, `twilio_api_key`, and `twilio_api_secret` are real values in Firestore.
- [ ] `APP_PUBLIC_BASE_URL` is the exact public HTTPS origin.
- [ ] TwiML App Voice URL is `https://YOUR_DOMAIN/call/voice` using `POST`.
- [ ] TwiML App Status Callback URL is `https://YOUR_DOMAIN/call/status-callback` using `POST`.
- [ ] At least one active Twilio number exists in Firestore `twilio_numbers` or `twilio_caller_id` is configured as fallback.
- [ ] Trial accounts have verified all destination numbers, or the account is upgraded.
- [ ] Browser pages are served over HTTPS for microphone/WebRTC support.
- [ ] `TWILIO_VALIDATE_SIGNATURE=true` is enabled after URL verification.
- [ ] Logs are monitored for `/call/voice`, generated TwiML, `/call/status-callback`, and `/call/dial-complete` events.

