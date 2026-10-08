# Step 8 — Cloud Server (AWS, free tier)

Owner's goals: **backup** and **access from both the computer and the phone**.
Replaces decision "Server: none" in [01](01-mvp-scope.md). No Firebase: reminders stay on the phone
(WorkManager), working on synced data.

## Shape

```
Phone app (Room, offline) ──sync──┐
                                  ├─ HTTPS ─► Lambda Function URL (Kotlin/JVM 21, SnapStart)
Browser (web UI served by Lambda) ┘                │
                                                   ▼
                                     DynamoDB table lapel-data (PITR 35 days)
```

| Piece | Choice | Why |
|---|---|---|
| Region | eu-central-1 (Frankfurt) | Closest full-service region to Israel, no opt-in |
| Compute | One Lambda, **Function URL** (no API Gateway) | Fixed HTTPS URL, no static IP, no extra cost |
| Code | Kotlin on JVM 21 + SnapStart, depends on `:domain` | Same profit/payment/reminder rules as the app |
| Data | DynamoDB, provisioned 5 RCU / 5 WCU, point-in-time recovery | Inside always-free; PITR is the backup |
| Web UI | Static Hebrew RTL single page served by the same Lambda | No S3 website/CloudFront to pay for or secure |
| Auth | Single password (GitHub secret → Lambda env) → signed token (HMAC, 30 days) | One user, no Cognito |
| Deploy | GitHub Actions → OIDC role from `infra/bootstrap.yaml` → AWS SAM | No long-lived AWS keys |
| Cost guard | AWS Budget $1/month, email at 50% actual / 100% forecast | Early warning |

## Data & sync
- Every record gets a **UUID** id, `updatedAt` (ms) and `deleted` flag (soft delete).
  Room moves to UUID primary keys in DB v3 (migration maps old numeric ids to UUIDs, keeps all data).
- DynamoDB single table: `pk = type` (`ORDER`, `CUSTOMER`, …), `sk = id`, plus `updatedAt`; a GSI on `updatedAt`
  is not needed at this size — sync reads changed items with a filter.
- Endpoints:
  - `POST /api/login` → token
  - `GET /api/sync?since=<ms>` → all records changed after `since`
  - `POST /api/sync` → records changed on the device; server keeps the newer `updatedAt` per record (last write wins)
- Phone: sync on app open, after each save (debounced) and every 6 hours in the background.
  First sync after the upgrade uploads everything already on the phone.
- Web: reads and writes the same endpoints.

## Phases
1. Bootstrap (owner, once): `infra/bootstrap.yaml` + two GitHub secrets — guide in [../setup-aws.md](../setup-aws.md).
2. Server: SAM template, Lambda handler, DynamoDB repository, auth, sync endpoints, unit tests with an in-memory store; deploy workflow.
3. App: UUID migration (DB v3), sync client + worker, login screen (server URL + password).
4. Web UI: dashboard, orders, customers, order detail with payments — Hebrew RTL.
