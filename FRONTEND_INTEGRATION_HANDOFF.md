# AR Frontend — Integration Contract and Verification Checklist (G-33)

> The frontend repository was **not available** to this work, so no frontend code was changed or tested. This is the
> backend contract as implemented, and the checks the frontend team must run. Backend authorization is the
> security boundary; hiding a button is not authorization.

Base URL: `https://<host>/paves/ar` (servlet context path `/paves/ar`). Every response is
`{ "success": bool, "message": string, "data": … , "timestamp": … }`. Errors: `400` validation/business rule
(`message` is user-readable), `401` missing/invalid token, `403` not permitted (`message` says which decision),
`404` not found, `409` recovery action not applicable (`data` carries the record's state).

## 1. Invoice statuses

`InvoiceStatus` (persisted): `GENERATED`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `READY_TO_SEND`.

- `APPROVED` = approved, document **not yet** stored (generation failed) → offer "Generate document"
  (`POST /api/v1/invoices/{id}/document`).
- `READY_TO_SEND` = approved **and** document stored. It does **not** mean sent.
- "Sent" is never an invoice status — it is the latest delivery (§3).

Generation workspace `GET /api/v1/invoice-generation/workspace`: `rows[].workspaceStatus` is
one of `READY_FOR_INVOICE`, `GENERATED`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `READY_TO_SEND` (G-11: the last
one previously crashed the whole response). `summary.readyToSendCount` is now populated. Unknown values must be
shown as-is, not dropped.

## 2. Document

`GET /api/v1/invoices/{id}` → `documentAvailable` is true only when the invoice is `READY_TO_SEND` **and** its PDF is
stored; `documentUrl` is then `"/api/v1/invoices/{id}/document"` — **relative to the API base, without the
`/paves/ar` context path**. Build the link as `<API base incl. /paves/ar>` + `documentUrl`, sending the bearer
token (the download requires authentication; a plain `<a href>` without the token gets 401).

## 3. Delivery (send to client)

- `POST /api/v1/invoices/{id}/send` — requires the invoice-delivery authority. Returns 200 with
  `data.deliveryStatus`:
  - `SENT` — done; the receivable is created.
  - `FAILED` — definitely not delivered; the user may send again.
  - `UNCERTAIN` — the mail server may have accepted it; **do not offer "send again"**; tell the user operations
    will confirm (message: "Invoice delivery outcome is unknown - do not send again").
- Refused with `400` before anything is sent: already sent ("already been sent"), a delivery in progress or
  uncertain ("outcome is unknown"), not `READY_TO_SEND`, no client email.
- `GET /api/v1/invoices/{id}/deliveries` — history, newest first; the first entry is the current delivery state.
  New fields: `requestedBy`, `resolvedAt`, `resolvedBy`, `resolutionReason`. Status `PENDING` = in progress.
- UX: enable "Send" only for `READY_TO_SEND` invoices whose latest delivery is absent or `FAILED`; disable the button
  while the request is in flight (the backend refuses duplicates anyway). Do not use localStorage as the source of
  delivery truth.

## 4. Approval

`POST /api/v1/invoices/{id}/approve` / `/reject` — invoice-approval authority; maker-checker: the submitter cannot
approve (403 with an explanatory message). Approval returns the invoice; if document generation failed the status
is `APPROVED` with `documentAvailable=false`.

## 5. Recovery (operations users only — `OPERATIONS_RECOVERY`)

`POST /api/v1/recovery/automatic-billing-runs/{runId}/requeue` `{reason}`,
`POST /api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval`,
`GET  /api/v1/recovery/automatic-billing-runs/{runId}/events`,
`POST /api/v1/recovery/invoices/{invoiceId}/receivable` `{reason}`,
`POST /api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` `{outcome: SENT|FAILED, reason}`.
`409` = not applicable now; show `message`.

## 6. CORS

Allowed origins are configured per environment (`AR_CORS_ALLOWED_ORIGINS`); none by default. A frontend served
from another origin must have its exact origin configured; credentials are allowed, allowed request headers are
`Authorization`, `Content-Type`, `Accept`, `X-Requested-With`.

## 7. Verification checklist (frontend team)

- [ ] Every status in §1 renders (including `READY_TO_SEND`); KPI counts include `readyToSendCount`.
- [ ] Workspace loads with at least one `READY_TO_SEND` invoice.
- [ ] Document link includes `/paves/ar` and the bearer token; works for `READY_TO_SEND`, absent otherwise.
- [ ] An approved-but-not-sent invoice is never shown as "Sent".
- [ ] "Send" disabled for sent/uncertain/in-progress invoices; `UNCERTAIN` shows "outcome unknown – do not resend".
- [ ] 400/403/409 `message` shown to the user (approval refused, maker-checker, already sent, recovery not applicable).
- [ ] Pagination/filtering/KPIs correct with all statuses present.
- [ ] Build and the frontend's own tests pass; an end-to-end run against a backend with this release.
