# Lapel Pin Order Manager — Architecture Plan

Android app (Kotlin, Jetpack Compose M3, Room, Retrofit, WorkManager, Hilt) for a lapel-pin reselling
business: track orders from client → Alibaba → FedEx → client, remind the owner to collect the 50% deposit
and 50% balance, and show real net profit.

1. [MVP scope & decisions](01-mvp-scope.md)
2. [Data model, Room schema & sync rules](02-data-model.md)
3. [FedEx integration & background sync](03-sync-strategy.md)
4. [UI / screen architecture](04-ui-screens.md)
5. [Profit calculation logic](05-profit-calculation.md)
6. [Development roadmap](06-roadmap.md)
7. [Review of the current spreadsheet](07-spreadsheet-review.md)
