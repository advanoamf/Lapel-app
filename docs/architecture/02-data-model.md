# Step 2 — Data Model, Room Schema & Sync Rules

## Conventions
- **Money** is stored as `Long` in **agorot** (₪1 = 100). No `Double` for money — avoids rounding errors.
- **Timestamps** are `Instant` (epoch millis); **calendar dates** (order date, payment date) are `LocalDate` (epoch day). Converted with Room `TypeConverter`s.
- **Room is the single source of truth.** UI reads only `Flow`s from Room. The network layer writes into Room; it never talks to the UI.
- **Derived values are never stored** (payment status, profit, outstanding balance). They are computed in SQL views / domain mappers so they can never go stale.

## Entity relationship

```
Customer 1───* Order 1───* OrderItem *───0..1 Design
                  │ 1───* Payment
                  │ 1───* OrderCost
                  │ 1───* Shipment 1───* TrackingEvent
                  │ 1───* StatusChange
                  └ 1───* ReminderLog
```

## Room entities

```kotlin
@Entity(tableName = "customers", indices = [Index("name")])
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String?,            // E.164, used for call / WhatsApp intents
    val email: String?,
    val organization: String?,     // branch / organization – "סניף" (e.g. a youth-movement branch)
    val paymentTermsDays: Int = 0, // 0 = pay on delivery; 60 = "שוטף+60"
    val notes: String?,
    val customerSince: LocalDate,  // editable "date added", defaults to today
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "orders",
    foreignKeys = [ForeignKey(CustomerEntity::class, ["id"], ["customerId"], onDelete = RESTRICT)],
    indices = [Index("customerId"), Index("fulfillmentStatus"), Index("orderDate")],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val orderNumber: String,               // keeps existing numbering: "091", "008B", "102-148"
    val title: String,                     // e.g. "Team logo pins – 200 pcs"
    val orderDate: LocalDate,              // editable, defaults to today
    val dueDate: LocalDate?,               // promised delivery date to client
    val fulfillmentStatus: FulfillmentStatus,
    val depositPercent: Int = 50,          // payment plan; 50/50 by default
    val discountAgorot: Long = 0,          // agreed discount / adjustment to the client
    val deliveryMethod: DeliveryMethod,    // FEDEX, SELF_PICKUP
    val qualityOk: Boolean?,               // "תקינות?" – received OK
    val alibabaOrderNumber: String?,
    val alibabaOrderedOn: LocalDate?,
    val supplierName: String?,
    val deliveredAt: Instant?,             // set by FedEx sync (or manually)
    val completedAt: Instant?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class FulfillmentStatus { DRAFT, ORDERED_FROM_ALIBABA, SHIPPED, DELIVERED, COMPLETED, CANCELLED }

@Entity(tableName = "order_items", foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = CASCADE)], indices = [Index("orderId")])
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val designId: Long?,           // reorders reuse a Design (mold already paid)
    val designName: String,
    val pinType: PinType,          // SOFT_ENAMEL, HARD_ENAMEL, DIE_STRUCK, PRINTED, OTHER
    val sizeMm: Int?,
    val plating: String?,          // gold, silver, black nickel…
    val quantityOrdered: Int,      // from supplier (incl. spares)
    val quantitySold: Int,         // billed to the client
    val unitPriceAgorot: Long,     // selling price per pin (₪)
    val artworkUri: String?,       // copied into app-private storage
)

@Entity(tableName = "order_costs", foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = CASCADE)], indices = [Index("orderId")])
data class OrderCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val type: CostType,            // ALIBABA_PAYMENT (₪ charged: pins + mold + shipping), CUSTOMS, REFERRAL_COMMISSION,
                                   // BANK_FEE (₪8 added automatically to new orders), FEDEX, OTHER
    val amountAgorot: Long,
    val note: String?,
)

@Entity(tableName = "designs")
data class DesignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // "סיכת קווה"
    val artworkUri: String?,
    val supplierName: String?,
    val moldPaidOnOrderId: Long?,  // reorders of this design add no mold cost
    val notes: String?,
)

@Entity(tableName = "payments", foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = CASCADE)], indices = [Index("orderId")])
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val amountAgorot: Long,
    val method: PaymentMethod,     // BIT, PAYBOX, BANK_TRANSFER, CASH, OTHER
    val milestone: PaymentMilestone, // DEPOSIT, BALANCE, OTHER (pre-selected, editable)
    val receivedOn: LocalDate,
    val reference: String?,        // bank ref / Bit confirmation
    val createdAt: Instant,
)

@Entity(
    tableName = "shipments",
    foreignKeys = [ForeignKey(OrderEntity::class, ["id"], ["orderId"], onDelete = CASCADE)],
    indices = [Index("orderId"), Index(value = ["trackingNumber"], unique = true)],
)
data class ShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val trackingNumber: String,
    val status: ShipmentStatus,    // see mapping table below
    val statusDescription: String?,// FedEx text, shown in UI
    val lastEventAt: Instant?,
    val shippedAt: Instant?,       // first "picked up" scan
    val deliveredAt: Instant?,
    val estimatedDelivery: Instant?,
    val receivedBy: String?,       // FedEx proof-of-delivery signature name
    val lastSyncedAt: Instant?,
    val syncError: String?,        // last error, shown as a warning chip
    val trackingActive: Boolean = true, // false once delivered or manually stopped
)

enum class ShipmentStatus { PENDING, LABEL_CREATED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION, UNKNOWN }

@Entity(
    tableName = "tracking_events",
    foreignKeys = [ForeignKey(ShipmentEntity::class, ["id"], ["shipmentId"], onDelete = CASCADE)],
    indices = [Index(value = ["shipmentId", "occurredAt", "eventCode"], unique = true)], // idempotent upserts
)
data class TrackingEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    val occurredAt: Instant,
    val eventCode: String,         // FedEx code: PU, IT, AR, DP, OD, DL, DE…
    val description: String,
    val location: String?,         // "MEMPHIS, TN, US"
)

@Entity(tableName = "status_changes", indices = [Index("orderId")])
data class StatusChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val from: FulfillmentStatus?,
    val to: FulfillmentStatus,
    val source: ChangeSource,      // MANUAL, FEDEX_SYNC, SYSTEM (auto-complete)
    val at: Instant,
)

@Entity(tableName = "reminder_log", indices = [Index(value = ["orderId", "type"])])
data class ReminderLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val type: ReminderType,        // DEPOSIT_DUE, BALANCE_DUE, SHIPPED, DELIVERED, SYNC_FAILED
    val sentAt: Instant,
    val snoozedUntil: Instant?,
)
```

### Read model (what the screens consume)

A Room `@DatabaseView` / `@Relation` query produces one row per order with totals already summed in SQL:

```kotlin
data class OrderSummary(
    val orderId: Long, val customerName: String, val title: String,
    val orderDate: LocalDate, val fulfillmentStatus: FulfillmentStatus, val depositPercent: Int,
    val sellingTotal: Long,   // SUM(quantitySold * unitPriceAgorot) - discountAgorot
    val costTotal: Long,      // SUM(order_costs.amountAgorot)
    val paidTotal: Long,      // SUM(payments.amountAgorot)
    val deliveredAt: Instant?, val activeShipments: Int,
)
// Domain mapper adds: paymentStatus, depositDue, balanceDue, outstanding, netProfit, marginPct, isOverdue
```

## Remote models (FedEx Track API)

Only one remote system. Two calls:

1. `POST /oauth/token` (client_credentials) → `access_token`, valid ~1 hour. Cached in memory + encrypted prefs; refreshed by an OkHttp `Authenticator` on 401.
2. `POST /track/v1/trackingnumbers` — up to **30 tracking numbers per request**, so all active shipments sync in one call.

```kotlin
// Request
data class TrackRequestDto(
    val includeDetailedScans: Boolean = true,
    val trackingInfo: List<TrackingInfoDto>,           // [{ trackingNumberInfo: { trackingNumber } }]
)
// Response (only the fields we use; kotlinx.serialization ignoreUnknownKeys = true)
data class TrackResponseDto(val output: OutputDto)
data class OutputDto(val completeTrackResults: List<CompleteTrackResultDto>)
data class CompleteTrackResultDto(val trackingNumber: String, val trackResults: List<TrackResultDto>)
data class TrackResultDto(
    val latestStatusDetail: StatusDetailDto?,          // code, derivedCode, description
    val dateAndTimes: List<DateAndTimeDto>?,           // type = ACTUAL_PICKUP, ACTUAL_DELIVERY, ESTIMATED_DELIVERY…
    val scanEvents: List<ScanEventDto>?,               // date, eventType, eventDescription, scanLocation
    val deliveryDetails: DeliveryDetailsDto?,          // receivedByName
    val error: ErrorDto?,                              // e.g. TRACKING.TRACKINGNUMBER.NOTFOUND
)
```

### FedEx code → app status mapping

| FedEx `latestStatusDetail.code` | `ShipmentStatus` | Order effect | Notification |
|---|---|---|---|
| `OC` (label created) | LABEL_CREATED | none | — |
| `PU`, `IT`, `AR`, `DP`, `AF`, `CC`, `CD` | IN_TRANSIT | → `SHIPPED` | **Dispatched** (once) |
| `OD` | OUT_FOR_DELIVERY | stays `SHIPPED` | optional "Out for delivery" |
| `DL` | DELIVERED | → `DELIVERED` when **all** active shipments delivered | **Delivered + collect ₪X balance** |
| `DE`, `SE`, `CA`, `RS` | EXCEPTION | none | "Shipment problem" |
| tracking-number-not-found | PENDING (keep retrying for 7 days, then UNKNOWN) | none | — |

## How local and remote statuses sync

1. **The network only writes `shipments` and `tracking_events`.** It never edits `orders` directly.
2. In the same Room `@Transaction`, the **`OrderStatusReconciler`** (pure Kotlin, unit-tested) recomputes the order's fulfillment status from its shipments:
   - **Forward-only:** it can move `ORDERED → SHIPPED → DELIVERED`, but never moves backwards.
   - **Manual wins:** `COMPLETED` and `CANCELLED` are never touched by sync. If you manually set `DELIVERED`, sync won't undo it.
   - **Multi-shipment:** `SHIPPED` when any shipment is in transit; `DELIVERED` only when every active shipment is delivered.
   - Every change writes a `StatusChangeEntity` with `source = FEDEX_SYNC`.
3. After the transaction, **change events** (newly shipped / newly delivered) go to the `NotificationDispatcher`, which checks `reminder_log` so each notification fires **once**.
4. **Auto-complete:** when an order is `DELIVERED` and `paidTotal ≥ sellingTotal`, the reconciler moves it to `COMPLETED` (`source = SYSTEM`). This runs both after sync and after you record a payment.
5. **Idempotent:** tracking events are upserted on a unique key `(shipmentId, occurredAt, eventCode)`, so re-polling the same data changes nothing.
6. Delivered shipments get `trackingActive = false` and are no longer polled.

## Payment status (derived, never stored)

```
depositDue = sellingTotal * depositPercent / 100
UNPAID        paidTotal == 0
DEPOSIT_PAID  0 < paidTotal < sellingTotal      (warn if paidTotal < depositDue: "partial deposit")
FULLY_PAID    paidTotal == sellingTotal
OVERPAID      paidTotal >  sellingTotal         (warning chip)
```

## Stock pins (v1)

The spreadsheet also tracks **group designs bought in bulk and sold piece by piece** to many individuals
(own sheet per design, ₪10–12 per pin, domestic shipping charged or self pickup, remaining stock counted).

```kotlin
@Entity(tableName = "stock_batches")
data class StockBatchEntity(        // one supplier purchase of a design for stock; costs as in an order
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val designId: Long, val quantityReceived: Int, val purchasedOn: LocalDate,
    // batch costs (Alibaba ₪, customs, bank fee) live in order_costs-style rows: stock_batch_costs
)

@Entity(tableName = "stock_sales")
data class StockSaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long, val customerId: Long, val soldOn: LocalDate,
    val quantity: Int, val unitPriceAgorot: Long,
    val shippingChargedAgorot: Long,  // e.g. ₪20 when mailed, 0 for pickup
    val shippingCostAgorot: Long,     // what the post office charged
    val sent: Boolean, val arrived: Boolean,
    val postTrackingNumber: String?,  // Israel Post "RR…IL" – shown/copied, not auto-tracked
)
// remaining stock = quantityReceived − SUM(stock_sales.quantity) − written-off
// payments for stock sales reuse the payments table (orderId nullable + stockSaleId)
```
