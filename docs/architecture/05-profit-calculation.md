# Step 5 — Profit Calculation Logic

All money is `Long` agorot. All formulas live in one pure-Kotlin function, `OrderFinancials.from(summary)`,
used by every screen, the dashboard and CSV export — so the numbers are identical everywhere. Nothing derived is stored.

## Inputs per order

| Input | Source |
|---|---|
| `sellingTotal` | Σ `quantitySold × unitPriceAgorot` − `discountAgorot` |
| `costTotal` | Σ `order_costs.amountAgorot` — Alibaba payment (₪ charged), customs, referral commission, bank fee (₪8 default), FedEx, other |
| `paidTotal` | Σ `payments.amountAgorot` |
| `depositPercent` | order field, default 50 |
| `totalQuantity` | Σ `quantitySold` |

Compared with the spreadsheet: costs are the real ₪ amounts (no exchange-rate cell to point at the wrong row),
**customs is always included**, and the ₪8 bank fee is a normal cost line instead of a hand-typed `+8`.

`costPerPin` uses the Alibaba payment ÷ **quantity ordered** (spares included); profit uses **quantity sold**.

## Per-order formulas

```kotlin
data class OrderFinancials(
    val sellingTotal: Long,
    val costTotal: Long,
    val paidTotal: Long,
    val netProfit: Long,          // sellingTotal − costTotal
    val marginPercent: Double?,   // netProfit / sellingTotal × 100, null if sellingTotal == 0
    val depositDue: Long,         // sellingTotal × depositPercent / 100, rounded half-up to whole agora
    val depositOutstanding: Long, // max(0, depositDue − paidTotal)
    val outstanding: Long,        // max(0, sellingTotal − paidTotal)   ← "customer debt"
    val dueNow: Long,             // see below
    val cashPosition: Long,       // paidTotal − costTotal  (are you out of pocket right now?)
    val costPerPin: Long?,        // costTotal / totalQuantity
    val paymentStatus: PaymentStatus,
)
```

**`dueNow`** — what the customer should already have paid, by your 50/50 rule:

| Fulfillment status | dueNow |
|---|---|
| DRAFT | 0 |
| ORDERED_FROM_ALIBABA, SHIPPED | `depositOutstanding` |
| DELIVERED, COMPLETED | `outstanding` (everything) |
| CANCELLED | 0 |

**`cashPosition`** is shown on the order when negative ("You've paid ₪400 more than you received") — useful while waiting for the balance.

### Worked example
200 pins × ₪15 = **₪3,000** selling. Costs: Alibaba ₪1,350 + supplier shipping ₪220 + bank fee ₪30 = **₪1,600**.
Deposit paid via Bit ₪1,500.

| Field | Value |
|---|---|
| Net profit | 3,000 − 1,600 = **₪1,400** |
| Margin | 1,400 / 3,000 = **46.7 %** |
| Deposit due | ₪1,500 → outstanding 0 → status **Deposit paid** |
| Outstanding | **₪1,500** |
| Due now (while shipped) | ₪0 → after FedEx "Delivered" → **₪1,500** + reminder |
| Cash position | 1,500 − 1,600 = **−₪100** |
| Cost per pin | ₪8.00 |

## Dashboard aggregates

| Card | Formula |
|---|---|
| **Money to collect** | Σ `outstanding` for orders not DRAFT / CANCELLED; subtitle "due now" = Σ `dueNow` |
| **Net profit this month** | Σ `netProfit` of orders whose **`deliveredAt` falls in the month** (sale is "earned" when the client receives it). Plus a smaller "expected" line: Σ `netProfit` of orders in progress. |
| **Margin this month** | Σ netProfit / Σ sellingTotal for the same orders (weighted, not average of %s) |
| **In transit** | count of orders with status SHIPPED |
| **Overdue** | count where `dueNow > 0` and the matching reminder is past its grace period |

Monthly profit by delivered month matches your cash flow (balance arrives on delivery). If you prefer "by order date", it is a one-line change in the query.

### Cancelled orders
Profit = `paidTotal − costTotal` (deposit kept minus money already spent). Counted in the month cancelled. Excluded from "Money to collect".

## Edge cases
- `sellingTotal == 0` → margin shown as "—".
- Overpayment → `outstanding = 0`, Overpaid chip, amount shown so you can refund or credit.
- Costs entered later (e.g. a FedEx bill arrives after delivery) simply update profit everywhere instantly — no recalculation job needed because nothing is stored.
- VAT is not modelled; enter prices and costs consistently (both with or both without VAT).

## Tests
Table-driven JUnit tests for `OrderFinancials` (the worked example, zero totals, rounding of odd agorot at 50 %, overpayment, cancelled) and Room DAO tests for the SQL sums and the month boundary (Asia/Jerusalem time zone).
