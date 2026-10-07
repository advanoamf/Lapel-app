# Step 4 — UI / Screen Architecture

Jetpack Compose + Material 3, single activity, type-safe Navigation Compose (`@Serializable` routes).
Each screen = `Route` composable (gets ViewModel via Hilt) + stateless `Screen(state, onEvent)` composable (previewable, testable).
ViewModels expose one `StateFlow<UiState>` built from Room flows with `combine` + `stateIn(WhileSubscribed(5_000))`.

## Navigation graph

```
Onboarding (first run: notifications permission, FedEx credentials – skippable)
└── Main (bottom bar: Dashboard · Orders · Stock · Customers · Settings)
    ├── Dashboard ───────────────┐
    ├── Orders ── OrderDetail ───┼── OrderEdit
    │               ├─ RecordPaymentSheet
    │               ├─ AddTrackingSheet (type / paste / scan)
    │               └─ AddCostSheet
    ├── Stock ── StockDesignDetail (sales list, remaining) ── AddStockSaleSheet
    ├── Customers ── CustomerDetail ── CustomerEdit
    └── Settings ── FedExCredentials / Reminders / Backup & Export
Deep link: lapel://order/{id} → OrderDetail
FAB on Dashboard & Orders: "New order"
```

## Screens

### 1. Dashboard
- **Banner** (only when needed): red "FedEx login failed" / grey "Last synced 2 h ago".
- **Summary cards** (tap → filtered order list):
  - 💰 **Money to collect** — ₪ total, with "₪X due now" underneath
  - 📈 **Net profit — this month** — ₪ + margin %, vs last month
  - 🚚 **In transit** — count
  - ⚠️ **Overdue** — count, red when > 0
- **"Needs action" list** — top 5 orders sorted by urgency (overdue balance → delivered unpaid → deposit missing → old drafts), each with a one-tap action.
- Pull-to-refresh → `SyncNowWorker`.

### 2. Orders list
- Search (customer, title, tracking no., Alibaba no.).
- Filter chips: **All · Unpaid · In transit · Delivered-unpaid · Completed · Overdue** (+ Drafts, Cancelled in overflow).
- Order card: customer + title, order date, **two status chips** (fulfillment + payment), outstanding ₪, profit ₪, tiny shipment icon with live FedEx state.

### 3. Order detail (the main working screen)
- **Header:** customer (tap → call / WhatsApp), order date, due date.
- **Fulfillment stepper:** Draft → Ordered → Shipped → Delivered → Completed. Each step shows its date and source icon (✋ manual / 📦 FedEx / ⚙ auto). The next manual step is a button ("Mark ordered from Alibaba").
- **Money card:** Selling ₪ · Received ₪ · **Outstanding ₪** (big) · Costs ₪ · **Net profit ₪ (margin %)**. Progress bar showing the deposit (50%) and balance segments. Button **Record payment**.
- **Shipments:** per tracking number — status chip, last event + location, ETA, "synced 12 min ago" / error chip, expandable event timeline, copy/open-in-FedEx action.
- **Items** (designs, qty, price), **Costs**, **Payments**, **History** (status changes) as collapsible sections.

### 4. Order edit
Single scrolling form: customer picker (+ create inline), title, order date (date picker, default today), due date, deposit % (default 50), line items list (add/remove, artwork photo from gallery/camera), Alibaba order no. + date, supplier, notes. Validation inline; "Save as draft" vs "Save".

### 5. Bottom sheets
- **Record payment:** amount pre-filled with what's due (deposit or balance), method chips **Bit · PayBox · Bank · Cash · Other**, date (today), reference. Shows "after this: ₪X still owed".
- **Add tracking:** text field + **Scan** (Google code scanner — no camera permission needed) + paste-from-clipboard suggestion. Validates FedEx format (12/15/20/22 digits) and triggers an immediate sync.
- **Add cost:** type chips (Alibaba goods · Supplier shipping · FedEx · Bank/FX fee · Other), amount ₪, note.

### 6. Stock pins
List of stock designs with **remaining pins** and money still owed. Design detail: batches bought, sales list (buyer, qty, price, mail ₪20 / pickup, sent ✓, arrived ✓, Israel Post number), "Add sale" sheet with quick 50/50 payment buttons. Low-stock warning.

### 7. Customers
List with search and outstanding badge. Detail: contact actions, customer since, totals (orders, revenue, profit, owed), order list. Edit form includes **customer since** date.

### 8. Settings
FedEx credentials + Test connection + environment (debug only) · sync interval · reminder interval (days) & daily reminder time · notification toggles per channel · CSV export (orders, payments, costs) · backup/restore DB file · app language.

## Status indicators (consistent everywhere)

| Indicator | Look |
|---|---|
| Fulfillment chip | Draft grey · Ordered blue · Shipped indigo · Delivered teal · Completed green · Cancelled strikethrough grey |
| Payment chip | Unpaid red · Deposit paid amber · Fully paid green · Overpaid purple |
| Overdue | red dot + "Overdue 5 d" label |
| FedEx sync | 📦 + "synced 12 min ago"; spinner while syncing; ⚠ chip with message on error |

Colors come from Material 3 theme tokens (light + dark, dynamic color off for consistent status colors); every chip also has an icon/text so color is never the only signal.

## Language & layout
**Hebrew is the default language** (`values/strings.xml`), English in `values-en/`; Compose mirrors layout automatically for RTL. Amounts formatted with `NumberFormat` for `he-IL` (₪1,234.50). Dates `dd/MM/yyyy`.
