# Step 3 — FedEx Integration & Background Sync

There is exactly one remote system (FedEx Track API) and no server. Everything below runs on the phone.

## Layering

```
ui (Compose screens, ViewModels)          ← observe Flow<…> from repositories only
domain (use cases, pure Kotlin)           ← OrderStatusReconciler, OrderFinancials, ReminderPolicy
data
 ├─ local  (Room DAOs)                    ← single source of truth
 ├─ remote (Retrofit FedExApi, DTOs)      ← never seen by ui/domain
 └─ repository (TrackingRepository, OrderRepository, …)
work (WorkManager workers)                ← call repositories/use cases, then NotificationDispatcher
```

## Network stack

| Piece | Choice |
|---|---|
| HTTP | Retrofit + OkHttp, `kotlinx.serialization` converter (`ignoreUnknownKeys = true`) |
| Base URL | `https://apis.fedex.com` (production), `https://apis-sandbox.fedex.com` (sandbox — switch in Settings, debug builds) |
| Auth | `POST /oauth/token` with `grant_type=client_credentials`. `FedExTokenProvider` caches the token in memory until ~5 min before expiry. An OkHttp **interceptor** adds `Authorization: Bearer …`; an OkHttp **Authenticator** refreshes once on `401` (guarded by a `Mutex` so parallel calls don't refresh twice). |
| Tracking call | `POST /track/v1/trackingnumbers`, `includeDetailedScans = true`, **≤ 30 numbers per call** (all active shipments are chunked). |
| Credentials | Entered once in **Settings → FedEx** (API key + secret key). Stored in DataStore, encrypted with an AES-GCM key held in the **Android Keystore**. Never logged, excluded from backups. A **"Test connection"** button fetches a token and shows ✓ / ✗. |
| Timeouts | connect 15 s, read 30 s. HTTP logging only in debug builds, with `Authorization` redacted. |

```kotlin
interface FedExApi {
    @FormUrlEncoded @POST("oauth/token")
    suspend fun token(
        @Field("grant_type") grantType: String = "client_credentials",
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
    ): TokenDto

    @POST("track/v1/trackingnumbers")
    suspend fun track(@Body body: TrackRequestDto): TrackResponseDto
}
```

## TrackingRepository.syncActiveShipments()

1. Load shipments where `trackingActive = true`. **If none → return immediately** (no network, no battery).
2. Chunk into groups of 30 → call `track()` for each chunk.
3. For each result, in one Room `@Transaction`:
   - map DTO → `ShipmentStatus` (table in Step 2), upsert `ShipmentEntity` + `TrackingEventEntity`s;
   - per-number errors (e.g. *not found yet*) go into `shipment.syncError`, the rest continue;
   - run `OrderStatusReconciler` for the affected order (forward-only, writes `StatusChange`).
4. Return a `SyncReport(newlyShipped, newlyDelivered, exceptions, errors)` → `NotificationDispatcher`.
5. Housekeeping: `DELIVERED` → `trackingActive = false`. A shipment not delivered **45 days** after creation is deactivated and the owner is notified once ("Tracking stopped for …").

## Workers

| Worker | Type | Schedule | Constraints | Purpose |
|---|---|---|---|---|
| `TrackingSyncWorker` | Periodic, unique `"tracking-sync"`, policy `UPDATE` | every **2 h** (Settings: 1 / 2 / 4 / 6 h) | `NetworkType.CONNECTED` | `syncActiveShipments()` |
| `TrackingSyncWorker` (fast lane) | One-time, unique `"tracking-fast"`, `REPLACE` | **+30 min** while any shipment is `OUT_FOR_DELIVERY` | CONNECTED | Get the "Delivered" alert sooner on delivery day |
| `SyncNowWorker` | One-time, expedited, unique `"tracking-now"`, `KEEP` | pull-to-refresh, or right after adding a tracking number | CONNECTED | Immediate refresh |
| `ReminderWorker` | Periodic, unique `"reminders"` | **daily**, first run delayed to next **10:00** | none (offline) | Deposit / balance / overdue reminders |

All are `@HiltWorker CoroutineWorker`s. Periodic work is enqueued idempotently in `Application.onCreate()`; WorkManager itself persists jobs across reboots and app updates.

### Error handling

| Failure | Worker result | User sees |
|---|---|---|
| No network / timeout / `5xx` / `429` | `Result.retry()` — exponential backoff from 30 s, give up after 5 attempts (next periodic run tries again) | nothing; "Last synced" time on dashboard |
| `401`/`403` after a token refresh (bad or expired credentials) | `Result.failure()` | **"FedEx login failed – check Settings"** notification (once per day max) + red banner on dashboard |
| Tracking number not found | success for the batch; `syncError` on that shipment | grey "Waiting for FedEx" chip (normal for the first 24–48 h after label creation) |
| Unknown FedEx status code | mapped to `UNKNOWN`, raw text kept | FedEx description shown as-is; logged for a later mapping update |

### Battery & Android restrictions
- Periodic work is batched by the OS and deferred in Doze — fine here, because a few hours' delay on "dispatched" doesn't matter, and the fast lane covers delivery day.
- No exact alarms, no foreground service, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- Typical load: a handful of active shipments → 1 HTTPS call every 2 h.

## ReminderWorker logic (`ReminderPolicy`, pure Kotlin, unit-tested)

Runs daily; each candidate is checked against `reminder_log` so nothing is sent twice too soon.

| Reminder | Condition | Repeat |
|---|---|---|
| **Deposit missing** | status `ORDERED_FROM_ALIBABA` (or later) and `paidTotal < depositDue` | every N days (default 3) |
| **Collect balance** | status `DELIVERED` and not fully paid | right away (from sync) and then every N days |
| **Overdue** (escalation) | balance unpaid > `N` days after `deliveredAt` | daily, red badge on dashboard |
| **Draft getting old** | `DRAFT` older than 7 days | once ("Still waiting on Dani's order?") |

Snoozing writes `snoozedUntil`; recording a payment that covers the due amount stops the reminder automatically.

## Notifications

| Channel | Importance | Events |
|---|---|---|
| `payments` | High | Deposit missing, Collect balance, Overdue |
| `shipments` | Default | Dispatched, Out for delivery, Delivered, Shipment problem |
| `sync` | Low | FedEx login failed, Tracking stopped |

- Android 13+: request `POST_NOTIFICATIONS` during first-run onboarding.
- Tapping a notification deep-links to `lapel://order/{id}`.
- Actions on payment reminders: **Record payment** (opens the payment sheet pre-filled with the amount due), **WhatsApp** (opens `https://wa.me/<phone>` with a polite prefilled message), **Snooze 2 days** (handled in a `BroadcastReceiver`, no app launch).
- Delivered notification combines both facts: *"📦 Delivered to Dani — collect ₪600 balance"*.

## Testing the sync
- **MockWebServer** with recorded FedEx JSON fixtures (in transit, delivered, not found, multi-piece, 401) → repository tests.
- `OrderStatusReconciler` & `ReminderPolicy`: plain JUnit table tests (forward-only, manual override, multi-shipment, auto-complete).
- `WorkManagerTestInitHelper` + `TestDriver` to run workers deterministically.
- Final check against the FedEx **sandbox**, then 2–3 real orders in production.
