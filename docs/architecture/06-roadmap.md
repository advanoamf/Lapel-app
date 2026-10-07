# Step 6 — Development Roadmap

Ordered so the biggest pain point (forgetting to collect money) is solved **before** the FedEx integration.
Each phase ends with a working, installable app.

## Phase 0 — Project setup
- Android Studio project `com.lapel.app`, single `app` module, packages `ui / domain / data / work / di`.
- Gradle version catalog: Kotlin 2.x + Compose compiler plugin, Compose BOM (Material 3), Navigation Compose, Lifecycle, Hilt + KSP, Room + KSP (schema export on), Retrofit + OkHttp + kotlinx.serialization, WorkManager + hilt-work, DataStore, Play Services code scanner, JUnit, Turbine, MockWebServer, Robolectric.
- `minSdk 26`, `targetSdk`/`compileSdk` latest stable. ktlint/detekt, Android Lint.
- GitHub Actions: lint + unit tests + `assembleDebug` on every push.
- **Done when:** empty app with bottom navigation runs on the phone; CI green.

## Phase 1 — Data layer & domain core
- Room entities, DAOs, `OrderSummary` query, type converters, DB v1 schema exported.
- `OrderFinancials`, `PaymentStatus`, `OrderStatusReconciler` (pure Kotlin) with full unit tests.
- Repositories (`CustomerRepository`, `OrderRepository`, `PaymentRepository`).
- **Done when:** all domain tests pass; DAO tests on Robolectric pass.

## Phase 2 — Customers & orders
- Customers list / detail / edit (incl. "customer since").
- Order edit (line items, order date, deposit %), order list with search + filter chips, order detail skeleton with fulfillment stepper and manual status buttons.
- **Done when:** you can enter your real current orders.

## Phase 3 — Money
- Record payment sheet, add cost sheet, money card, payment & cost lists, auto-complete rule.
- Dashboard cards + "Needs action" list.
- **Done when:** profit and "money to collect" match a hand-calculated spreadsheet for your real orders.

## Phase 4 — Reminders & notifications (offline)
- Notification channels, onboarding permission, `ReminderPolicy`, `ReminderWorker`, `reminder_log`, actions (Record payment / WhatsApp / Snooze), deep links.
- Manual "Mark delivered" triggers the collect-balance reminder (works even before FedEx is connected).
- **Done when:** a delivered-unpaid test order nags you on schedule until you record payment.

## Phase 5 — FedEx integration
- FedEx developer project (Track API) → sandbox keys, then production keys linked to your FedEx account.
- Encrypted credentials screen + Test connection.
- `FedExApi`, token provider/authenticator, DTOs, status mapping, `TrackingRepository`, `TrackingSyncWorker` + fast lane + Sync now.
- Add-tracking sheet with scanner; shipment timeline UI; sync banners/chips.
- MockWebServer fixture tests; sandbox run; then real shipments.
- **Done when:** a real FedEx shipment moves the order to Shipped and Delivered by itself and fires the notifications.

## Phase 6 — Polish
- Hebrew + English strings, RTL check, dark mode, empty states, number/date formatting.
- CSV export, DB backup/restore file, Android Auto Backup rules (credentials excluded).
- Monthly profit view (simple list; charts are v1.1).

## Phase 7 — Testing & release
- Compose UI tests for the critical flows (create order → record deposit → mark ordered → delivered → record balance → completed).
- Room migration test harness in place for future versions.
- Two-week trial on your phone with live orders, then fix list.
- Signed release APK (installed directly) — or Google Play internal testing if you want automatic updates.

## v1.1 backlog
Charts, PDF quote/receipt, reorder template, supplier comparison, Google Drive backup / second device.
