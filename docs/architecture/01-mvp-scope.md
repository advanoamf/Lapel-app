# Step 1 — Feature Scope & MVP Definition (v1.0)

## Decisions (confirmed with owner)

| Topic | Decision |
|---|---|
| Payments | **No payment-gateway integration.** Customers pay outside the app (Bit, PayBox, bank transfer). The owner records payments manually; the app's job is to **remind** the owner to collect. |
| Payment schedule | **50% deposit** when the Alibaba order is placed, **50% balance** when the product is delivered to the client. |
| Server | **None (Option A).** Everything runs on-device; WorkManager polls FedEx directly. |
| Currency | **ILS (₪)** everywhere. Alibaba costs are entered as the ₪ amount actually charged to the card (no FX conversion in the app). |
| Carrier | **Always FedEx** → FedEx Track API, no aggregator. Owner has a FedEx account for production API credentials. |
| Order structure | One order can have **several pin designs (line items)** and **several FedEx tracking numbers**. |
| Users / devices | Single user, single phone. Room is the only database; backup via Android Auto Backup + manual export. |
| Overdue rule | Deposit overdue: order confirmed but deposit not recorded. Balance overdue: shipment **Delivered** and balance not recorded after N days (default 3, configurable). |

## Key design choice: two independent statuses

The original single pipeline (`Draft → Pending Payment → Ordered → Shipped → Delivered → Completed`)
mixes two different things. With a 50/50 schedule the order is *already shipped* while half the money is
still owed — exactly the case that gets forgotten. So every order has:

1. **Fulfillment status** (where the pins are) — set manually up to "Ordered", then **automatically by FedEx**:
   `DRAFT → ORDERED_FROM_ALIBABA → SHIPPED → DELIVERED → COMPLETED` (+ `CANCELLED`)
2. **Payment status** (how much money came in) — **calculated** from recorded payments, never typed:
   `UNPAID → DEPOSIT_PAID → FULLY_PAID` (+ `OVERPAID` warning)

`COMPLETED` is reached automatically when the order is `DELIVERED` **and** `FULLY_PAID`.

## MVP v1.0 — Must have

| Area | Features |
|---|---|
| Customers | Name, phone (tap → call / WhatsApp), email, company, notes, **"customer since" date**. Customer page with order history and total owed. |
| Orders | Customer, **order date** (editable, defaults to today), due date, line items (design name, pin type, size, plating, quantity, unit price, artwork photo), Alibaba order number + date, supplier name, notes. Status history log. |
| Payments (manual) | "Record payment" sheet: amount (pre-filled with the 50% due), method (Bit / PayBox / Bank / Cash / Other), date, reference. Deposit / Balance milestone auto-detected. |
| FedEx tracking | Add one or more tracking numbers per order (type, paste or scan barcode). Background polling, event timeline, auto status: Shipped / Delivered. |
| Reminders & notifications | Deposit not received; **"Delivered — collect ₪X balance from <customer>"**; repeating balance reminder every N days until paid; Shipment dispatched; Shipment delivered; FedEx sync failure. Notification actions: *Mark paid*, *Snooze*, *Call/WhatsApp*. |
| Financials | Selling price (sum of line items), costs (Alibaba goods, supplier shipping, FedEx, bank/FX fees, other), received, calculated net profit, margin %, outstanding balance. |
| Dashboard | Cards: **Money to collect**, **Net profit this month**, **In transit**, **Overdue**. Quick filters: Unpaid / In transit / Delivered-unpaid / Completed. Search. |
| Settings | FedEx API credentials (encrypted), balance reminder interval, sync interval, notification toggles, CSV export / backup. |

## Later (v1.1+)
- Profit charts per month / customer / pin type
- PDF quote / receipt (Hebrew + English)
- Duplicate order ("reorder") and supplier comparison
- Optional cloud backup (Google Drive) if a second phone is added

## Out of scope
- Payment gateways (Bit/PayBox have no public merchant APIs for this use)
- Alibaba API (no buyer-side order API exists — "Ordered from Alibaba" is one manual tap)
- Accounting / VAT filing (CSV export for the accountant)
