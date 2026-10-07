# Step 7 — Review of the Current Spreadsheet (1.12.25)

The owner's existing Excel workbook (5 sheets, ~75 orders since 07/2022) was reviewed to make sure the app
models the real business. Customer names and phone numbers are intentionally **not** copied into this repo.

## What the spreadsheet does today

| Sheet | Purpose | App equivalent |
|---|---|---|
| הזמנות (Orders) | Per order: units ordered, unit $, mold $, shipping $, supplier, customs ₪, referral ₪, total cost ₪, units sold, price/unit ₪, revenue, profit, USD rate (+3%) | Order + OrderItems + OrderCosts + `OrderFinancials` |
| אספקה ותשלום (Delivery & payment) | Order date, "OK?" check, 50% + 50% payments, balance, notes (e.g. "שוטף+60"), shipping method (FedEx / self) | Payments, payment terms, delivery method, `qualityOk` |
| סיכות (Pins) | Picture list per order | Artwork on OrderItem / Design |
| שרוליק / סיכת חיים | Stock designs sold piece-by-piece to individuals, with domestic shipping, "sent?", "arrived?", Israel Post tracking, remaining stock | Stock batches & stock sales (proposed) |

## Problems found

### 1. ~₪19,600 of possibly-unpaid balances are hidden (high impact)
From row 68 down, the balance formula on **אספקה ותשלום** points at the wrong row of the Orders sheet
(e.g. `J69 = I69+H69−הזמנות!O89` instead of `O69`). Rows 88–96 of the Orders sheet are empty, so these balances show **₪0** even though
**no payment is recorded**:

| Order # | Sold (₪) | Recorded paid (₪) | Sheet shows | Real balance |
|---|---|---|---|---|
| 36B | 816 | 0 | 0 | **−816** |
| 98 | 720 | 0 | 0 | **−720** |
| 97 | 660 | 0 | 0 | **−660** |
| 99 | 1,105 | 0 | 0 | **−1,105** |
| 100 | 600 | 514 | 0 | **−86** |
| 42I | 420 | 0 | 0 | **−420** |
| 91D | 4,800 | 4,240 | 0 | **−560** |
| 102-148 | 14,598 | 0 | 0 | **−14,598** |
| 149 | 660 | 0 | 0 | **−660** |

Plus balances the sheet does show: 040 (−30), 151 (−293), 152 (−600), 153 (−330).
Some of these may have been paid and just not typed in. **Each one should be checked against the bank / Bit / PayBox history.**
Rows 37–68 also show the **wrong phone number and order number** in the payment sheet for the same reason.

### 2. Exchange rate taken from other rows
Total-cost (L) and profit (P) formulas use the rate cell of a different row (e.g. `L35` uses `S88`, `P22` uses `S33`).
Since row ~30 every order uses the same fixed **3.84 + 3%** regardless of when it was paid, so profit for recent orders
is only as accurate as that one number. → **App stores the rate on each order.**

### 3. Inconsistent profit formula
Early rows include customs (`J`) in profit; from row 29 profit is `O − L`, and `L` doesn't include customs.
Some rows add a fixed `+8`, `−90`, `−200` by hand. → **One formula for all orders; manual adjustments become a named "discount/adjustment" or cost line.**

### 4. Data-entry slips
Quantity and price swapped in two rows (95, 36B) — totals still right by luck. Order 102-148 has text ("פירוט באפליקציה") in a number column. → **App validates input types.**

## What changes in the app because of this

1. **Costs in USD + per-order rate + 3% card fee** (as the owner already works), plus ₪ lines for customs, referral commission (המלצה), bank fee.
2. **Qty ordered vs qty sold** per design (spares are a real cost).
3. **Designs** reused across orders — reorders (008B, 25B…25E, 42B…42I, 91B…91D) carry no mold cost.
4. **Customer = contact + branch/organization** and **payment terms** (net+60 for some institutions).
5. **Delivery method** FedEx / self pickup; **"arrived OK?"** check.
6. **Stock pins** (bulk design, many small buyers, domestic post, stock count) — proposed v1 feature, awaiting confirmation.
7. **Import**: a one-time import of this workbook into the app (Phase 2), with a report of rows it could not read, so history and current debts carry over.
