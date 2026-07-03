# API contract (MVP slice)

Extracted from the verified iOS Hermex client (`HermesMobile/Networking/*`,
`HermesMobile/Models/*`). This is the source of truth for this Android client's DTOs and
request/response shapes — treat it as ground truth, do not invent fields. All response fields
are nullable/optional unless noted; the server may add or rename fields at any time, so decoding
must never crash on unknown or missing fields (`ignoreUnknownFields = true`).

Wire format is snake_case; Kotlin DTOs use `JsonNamingStrategy.SnakeCase` to map to camelCase.

## Auth & health

- `GET /health` → `{ status?, sessions?, active_streams?, uptime_seconds? }`
- `GET /api/auth/status` → `{ auth_enabled?, logged_in?, password_auth_enabled?, passkeys_enabled?, passwordless_enabled? }`
  - If `auth_enabled == true && password_auth_enabled == false` → passkey-only server. Not
    supported; show a blocking error, do not attempt password login.
- `POST /api/auth/login` body `{ "password": string }` → `{ ok?, message?, error? }`. On success
  the server sets an HTTP-only session cookie that must be persisted across process death.
- `POST /api/auth/logout` body `{}` → `{ ok?, message?, error? }`

**CSRF**: the server validates `Origin`/`Referer` against `Host` on POSTs. A non-browser client
must never set `Origin` or `Referer` — omitting them is what makes the server treat it as a
trusted non-browser client. Standard OkHttp/Retrofit calls already omit these by default; do not
add an interceptor that sets them.

**Session expiry**: any `401` from an authenticated endpoint means the cookie is stale — clear
cookie state and drop to a logged-out screen, but keep the remembered server URL so re-login is a
one-field affair. Exception: `401` from `/api/auth/login` itself is just "wrong password" and
must NOT clear auth state.

## Sessions

- `GET /api/sessions` → `{ sessions?: [SessionSummary], cli_count?, server_time?, server_tz? }`
- `GET /api/session?session_id=...&messages=1&msg_limit=50` → `{ session?: SessionDetail }`
- `POST /api/session/new` body `{ workspace?, model?, model_provider?, profile? }` → `{ session?: SessionDetail }`

### SessionSummary (all fields nullable, wire is snake_case)

```
session_id, title, workspace, model, model_provider, message_count(int),
created_at(double, unix seconds), updated_at(double), last_message_at(double),
pinned(bool), archived(bool), project_id, profile, input_tokens(int), output_tokens(int),
estimated_cost(double), active_stream_id, is_streaming(bool), is_cli_session(bool),
source_tag, session_source, source_label
```

### SessionDetail (SessionSummary fields, flat, plus)

```
messages?: [ChatMessage], pending_user_message?, context_length?(int),
threshold_tokens?(int), last_prompt_tokens?(int)
```

(Truncation/pagination metadata `_messages_truncated` / `_messages_offset` exists on the wire but
is out of scope for MVP — tolerant decoding means their presence never breaks anything.)

### ChatMessage (all fields nullable)

```
role (e.g. "user"/"assistant"/"tool")
content (string; may also arrive as a structured array — for MVP extract plain string content
         only, treat non-string content as null rather than crashing)
_ts (double, unix seconds) — prefer this over `timestamp` when both present
timestamp (double, unix seconds) — fallback
message_id (string OR int on the wire — coerce to string)
name
tool_call_id
reasoning (string, "thinking" text)
attachments — NOT modeled in MVP; must not break decoding if present
```

## Chat send + SSE streaming

- `POST /api/chat/start` body `{ session_id (required), message (required), workspace?, model?, model_provider?, profile? }` → `{ stream_id?, session_id?, error? }`
- `GET /api/chat/stream?stream_id={streamID}` — SSE. Standard `event: <name>\ndata: <json>\n\n`
  format. Required request headers: `Accept: text/event-stream`,
  `Cache-Control: no-cache, no-transform`, `Accept-Encoding: identity`. Carries the same session
  cookie as REST calls — the stream endpoint is itself authenticated. Long-lived connection; the
  server sends `:`-prefixed heartbeat comment lines periodically — these must be ignored, not
  treated as errors or events. The HTTP client's read timeout must be disabled (0 / infinite) for
  this connection, or it will be killed between heartbeats.
- `GET /api/chat/cancel?stream_id=...` → `{ ok?, cancelled?, stream_id?, error? }`

### SSE event types (MVP)

| event | data shape | handling |
|---|---|---|
| `token` | `{"text": "..."}` | append to in-flight assistant message buffer |
| `reasoning` | `{"text": "..."}` | append to a separate "thinking" buffer |
| `tool` | `{"event_type":"tool.started","name","preview","args":{...},"tid"?,"id"?,"tool_call_id"?,"tool_use_id"?,"call_id"?}` | insert a new tool-call card. Stable ID: first non-blank of `tid`, `id`, `tool_call_id`, `tool_use_id`, `call_id`, in that order. |
| `tool_complete` | same shape as `tool` plus `duration`(double, seconds), `is_error`(bool) | find the matching card by stable ID, mark completed |
| `done` | `{"session": SessionDetail?, "usage": {...}}` | terminal: finalize in-flight message, close stream, optionally patch title/usage from embedded session |
| `stream_end` | `{}` | terminal: finalize, close, no extra payload |
| `error` | `{"error": "..."}` or `{"message": "..."}` | show inline error, finalize partial content, close |
| `cancel` | `{}` | terminal: stream was cancelled elsewhere, finalize, close |
| anything else | — | log and ignore; must never crash the parser |

No reconnect/replay logic in this MVP (the iOS app has one via an `after_seq` mechanism on top of
the SSE `id:` field — explicitly deferred here; document as a known follow-up, not built now).
