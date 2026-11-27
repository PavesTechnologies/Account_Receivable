# AR RBAC — endpoint access matrix

Generated from the controllers in this repository. One row per endpoint.

**205 endpoints across 48 controllers.** 10 decided, 16 open and sensitive, 179 open.


## How to read this

| Column | Meaning |
|---|---|
| **Access** | The annotation on the endpoint today. Blank = none applied yet; the endpoint still accepts any authenticated UMS token. |
| **Evidence** | The document that decided it, or why it is still open. |

The three annotations:

| Annotation | Admits |
|---|---|
| `@ExecutiveOnly` | Finance_Executive |
| `@ManagerOnly` | Finance_Manager |
| `@AnyFinanceRole` | either role |

The roles are **not** a hierarchy. Neither inherits the other's rights.

> **Blank rows are not "allow".** They are undecided, and the endpoint keeps its
> previous behaviour: any valid UMS token reaches it. Each blank is an open
> question in `RBAC_OPEN_QUESTIONS.md`.


## Decided — annotation applied

| Controller | Method | Path | Access | Evidence |
|---|---|---|---|---|
| `CompanySignatureController` | GET | `/api/v1/company-profile/signature-assets/{signatureReference}` | `@AnyFinanceRole` | docs/authorized-signature.md §2: any verified token (tightened to finance roles) |
| `CompanySignatureController` | PUT | `/api/v1/company-profile/{companyProfileId}/signature` | `@ExecutiveOnly` | docs/authorized-signature.md §2: requires FINANCE_EXECUTIVE |
| `CompanySignatureController` | GET | `/api/v1/company-profile/{companyProfileId}/signature` | `@AnyFinanceRole` | docs/authorized-signature.md §2: any verified token (tightened to finance roles) |
| `CompanySignatureController` | DELETE | `/api/v1/company-profile/{companyProfileId}/signature` | `@ExecutiveOnly` | docs/authorized-signature.md §2: requires FINANCE_EXECUTIVE |
| `InvoiceApprovalController` | POST | `/api/v1/invoices/{invoiceId}/approve` | `@ManagerOnly` | UAT AUTH-1 (:63); ownership table (:180); API contract §5 "Finance Manager → E2 approve" |
| `InvoiceApprovalController` | POST | `/api/v1/invoices/{invoiceId}/document` | `@ManagerOnly` | API contract :65 — E3 requires INVOICE_APPROVAL, the same authority as E2 |
| `InvoiceApprovalController` | GET | `/api/v1/invoices/{invoiceId}/document` | `@AnyFinanceRole` | API contract §5: E4 listed under "Anyone" |
| `InvoiceApprovalController` | POST | `/api/v1/invoices/{invoiceId}/reject` | `@ManagerOnly` | UAT ownership table (:180) "Approve / reject invoices (Finance Manager)" |
| `InvoiceDeliveryController` | GET | `/api/v1/invoices/{invoiceId}/deliveries` | `@AnyFinanceRole` | API contract §5: E5 listed under "Anyone" |
| `InvoiceListController` | GET | `/api/v1/invoices/{invoiceId}` | `@AnyFinanceRole` | API contract §5: E1 listed under "Anyone" |

## Open — sensitive, role not recorded anywhere

These do something consequential, but no document names which of the two roles may do it.

| Controller | Method | Path | Why it matters |
|---|---|---|---|
| `AutomaticBillingRunRecoveryController` | GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| `AutomaticBillingRunRecoveryController` | POST | `/api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| `AutomaticBillingRunRecoveryController` | POST | `/api/v1/recovery/automatic-billing-runs/{runId}/requeue` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| `BillingConfigurationController` | PUT | `/api/billing-configurations/{billingConfigurationId}/approve` | Approval action — AR_AUTH_CONFIGURATION_APPROVAL |
| `BillingConfigurationController` | PUT | `/api/billing-configurations/{billingConfigurationId}/reject` | Rejection action — AR_AUTH_CONFIGURATION_APPROVAL |
| `InvoiceDeliveryController` | POST | `/api/v1/invoices/{invoiceId}/send` | API contract §5 assigns this to "Sender" — an actor that is neither of the two AR roles. Backend authority: AR_AUTH_INVOICE_DELIVERY. |
| `InvoiceRecoveryController` | POST | `/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| `InvoiceRecoveryController` | POST | `/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| `InvoiceRecoveryController` | POST | `/api/v1/recovery/invoices/{invoiceId}/receivable` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| `OperationsReportController` | GET | `/api/v1/operations/alerts` | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data |
| `OperationsReportController` | GET | `/api/v1/operations/billing-summary` | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data |
| `OperationsReportController` | GET | `/api/v1/operations/reconciliation` | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data |
| `RecordActionLockController` | POST | `/api/record-locks/{resourceType}/{resourceId}` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |
| `RecordActionLockController` | GET | `/api/record-locks/{resourceType}/{resourceId}` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |
| `RecordActionLockController` | DELETE | `/api/record-locks/{resourceType}/{resourceId}` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |
| `RecordActionLockController` | PUT | `/api/record-locks/{resourceType}/{resourceId}/heartbeat` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |

## Full inventory, by controller


### ArDashboardController  
`src/main/java/com/AccountReceivableManagement/controller/dashboard/ArDashboardController.java` — 2 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/dashboard/invoices` | `getInvoices` | — | _open — needs a decision_ |
| GET | `/api/v1/dashboard/summary` | `getSummary` | — | _open — needs a decision_ |

### AutomaticBillingRunRecoveryController  
`src/main/java/com/AccountReceivableManagement/controller/recovery/AutomaticBillingRunRecoveryController.java` — 3 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | `events` | — | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval` | `reconcileApproval` | — | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/requeue` | `requeue` | — | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |

### BillingConfigurationController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingConfigurationController.java` — 17 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/billing-configurations` | `getAllBillingConfigurations` | — | _open — needs a decision_ |
| POST | `/api/billing-configurations` | `create` | — | _open — needs a decision_ |
| GET | `/api/billing-configurations/available-projects` | `getAvailableProjectsForNewConfiguration` | — | _open — needs a decision_ |
| GET | `/api/billing-configurations/clients` | `getClients` | — | _open — needs a decision_ |
| POST | `/api/billing-configurations/draft` | `createDraft` | — | _open — needs a decision_ |
| GET | `/api/billing-configurations/pending-approvals` | `getPendingApprovals` | — | _open — needs a decision_ |
| POST | `/api/billing-configurations/preview-schedule` | `previewSchedule` | — | _open — needs a decision_ |
| GET | `/api/billing-configurations/project/{projectId}` | `getApprovedByProjectId` | — | _open — needs a decision_ |
| GET | `/api/billing-configurations/projects/{clientId}` | `getProjects` | — | _open — needs a decision_ |
| DELETE | `/api/billing-configurations/{billingConfigurationId}` | `deleteBillingConfiguration` | — | _open — needs a decision_ |
| GET | `/api/billing-configurations/{billingConfigurationId}` | `getBillingConfiguration` | — | _open — needs a decision_ |
| PUT | `/api/billing-configurations/{billingConfigurationId}` | `update` | — | _open — needs a decision_ |
| PUT | `/api/billing-configurations/{billingConfigurationId}/approve` | `approve` | — | Approval action — AR_AUTH_CONFIGURATION_APPROVAL |
| PATCH | `/api/billing-configurations/{billingConfigurationId}/deactivate` | `deactivateBillingConfiguration` | `@AnyFinanceRole` | FE + FM |
| PUT | `/api/billing-configurations/{billingConfigurationId}/draft` | `saveDraft` | — | _open — needs a decision_ |
| PUT | `/api/billing-configurations/{billingConfigurationId}/reject` | `reject` | — | Rejection action — AR_AUTH_CONFIGURATION_APPROVAL |
| PUT | `/api/billing-configurations/{billingConfigurationId}/submit` | `submitForApproval` | — | _open — needs a decision_ |

### BillingDataAcquisitionController  
`src/main/java/com/AccountReceivableManagement/controller/billing_data_acquisition/BillingDataAcquisitionController.java` — 2 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| POST | `/api/billing-data-acquisition/acquire` | `acquire` | — | _open — needs a decision_ |
| GET | `/api/billing-data-acquisition/active-configurations` | `getActiveConfigurations` | — | _open — needs a decision_ |

### BillingFixedPriceController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingFixedPriceController.java` — 5 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| DELETE | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | `delete` | — | _open — needs a decision_ |
| GET | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | `get` | — | _open — needs a decision_ |
| PUT | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | `update` | — | _open — needs a decision_ |
| GET | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | `getAll` | — | _open — needs a decision_ |
| POST | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | `create` | — | _open — needs a decision_ |

### BillingFrequencyMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingFrequencyMasterController.java` — 7 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/billing-frequency` | `getAllBillingFrequencies` | — | _open — needs a decision_ |
| POST | `/api/billing-frequency` | `createBillingFrequency` | — | _open — needs a decision_ |
| GET | `/api/billing-frequency/active` | `getActiveBillingFrequencies` | — | _open — needs a decision_ |
| DELETE | `/api/billing-frequency/{billingFrequencyId}` | `deleteBillingFrequency` | — | _open — needs a decision_ |
| GET | `/api/billing-frequency/{billingFrequencyId}` | `getBillingFrequencyById` | — | _open — needs a decision_ |
| PUT | `/api/billing-frequency/{billingFrequencyId}` | `updateBillingFrequency` | — | _open — needs a decision_ |
| PATCH | `/api/billing-frequency/{billingFrequencyId}/activate` | `activateBillingFrequency` | — | _open — needs a decision_ |

### BillingMilestonePlanController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingMilestonePlanController.java` — 5 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| DELETE | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | `delete` | — | _open — needs a decision_ |
| GET | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | `get` | — | _open — needs a decision_ |
| PUT | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | `update` | — | _open — needs a decision_ |
| GET | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | `getByBillingConfiguration` | — | _open — needs a decision_ |
| POST | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | `create` | — | _open — needs a decision_ |

### BillingOccurrenceController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingOccurrenceController.java` — 5 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/billing-occurrences` | `getOccurrences` | — | _open — needs a decision_ |
| GET | `/api/billing-occurrences/{occurrenceId}` | `getOccurrence` | — | _open — needs a decision_ |
| POST | `/api/billing-occurrences/{occurrenceId}/calculate-tax` | `calculateTax` | — | _open — needs a decision_ |
| POST | `/api/billing-occurrences/{occurrenceId}/invoice` | `generateInvoice` | — | _open — needs a decision_ |
| GET | `/api/billing-occurrences/{occurrenceId}/tax-calculation` | `getTaxCalculation` | — | _open — needs a decision_ |

### BillingRecurringController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingRecurringController.java` — 8 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/billing-recurring/billing-configuration/{billingConfigurationId}` | `getByBillingConfiguration` | — | _open — needs a decision_ |
| POST | `/api/billing-recurring/{billingConfigurationId}` | `create` | — | _open — needs a decision_ |
| DELETE | `/api/billing-recurring/{recurringConfigurationId}` | `delete` | — | _open — needs a decision_ |
| GET | `/api/billing-recurring/{recurringConfigurationId}` | `get` | — | _open — needs a decision_ |
| PUT | `/api/billing-recurring/{recurringConfigurationId}` | `update` | — | _open — needs a decision_ |
| POST | `/api/billing-recurring/{recurringConfigurationId}/renew` | `renew` | — | _open — needs a decision_ |
| GET | `/api/billing-recurring/{recurringConfigurationId}/renewal-history` | `getRenewalHistory` | — | _open — needs a decision_ |
| GET | `/api/billing-recurring/{recurringConfigurationId}/schedule` | `getBillingSchedule` | — | _open — needs a decision_ |

### BillingSnapshotController  
`src/main/java/com/AccountReceivableManagement/controller/billing_data_acquisition/BillingSnapshotController.java` — 3 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/billing-snapshots` | `getByBillingConfiguration` | — | _open — needs a decision_ |
| POST | `/api/v1/billing-snapshots` | `createBillingSnapshot` | — | _open — needs a decision_ |
| GET | `/api/v1/billing-snapshots/by-period` | `getByProjectAndPeriod` | — | _open — needs a decision_ |

### BillingTMRateCardController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingTMRateCardController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| DELETE | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | `deleteRateCard` | — | _open — needs a decision_ |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | `getRateCard` | — | _open — needs a decision_ |
| PUT | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | `updateRateCard` | — | _open — needs a decision_ |
| GET | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | `getAllRateCards` | — | _open — needs a decision_ |
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | `addRateCard` | — | _open — needs a decision_ |
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards/save` | `saveRateCard` | — | _open — needs a decision_ |

### BillingTMRateDiscountController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingTMRateDiscountController.java` — 5 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | `getDiscounts` | — | _open — needs a decision_ |
| POST | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | `addDiscount` | — | _open — needs a decision_ |
| DELETE | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | `deleteDiscount` | — | _open — needs a decision_ |
| GET | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | `getDiscount` | — | _open — needs a decision_ |
| PUT | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | `updateDiscount` | — | _open — needs a decision_ |

### BillingTypeMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/BillingTypeMasterController.java` — 7 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/billing-types` | `getAllBillingTypes` | — | _open — needs a decision_ |
| POST | `/api/billing-types` | `createBillingType` | — | _open — needs a decision_ |
| GET | `/api/billing-types/active` | `getActiveBillingTypes` | — | _open — needs a decision_ |
| DELETE | `/api/billing-types/{billingTypeId}` | `deleteBillingType` | — | _open — needs a decision_ |
| GET | `/api/billing-types/{billingTypeId}` | `getBillingTypeById` | — | _open — needs a decision_ |
| PUT | `/api/billing-types/{billingTypeId}` | `updateBillingType` | — | _open — needs a decision_ |
| PATCH | `/api/billing-types/{billingTypeId}/activate` | `activateBillingType` | — | _open — needs a decision_ |

### ClientBudgetSummaryController  
`src/main/java/com/AccountReceivableManagement/controller/client/ClientBudgetSummaryController.java` — 2 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| POST | `/client-budget-summary/refresh/{clientId}` | `refreshBudget` | — | _open — needs a decision_ |
| GET | `/client-budget-summary/{clientId}` | `getClientBudget` | — | _open — needs a decision_ |

### CompanyProfileController  
`src/main/java/com/AccountReceivableManagement/controller/company_profile/CompanyProfileController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/company-profile` | `getActive` | — | _open — needs a decision_ |
| POST | `/api/v1/company-profile` | `create` | — | _open — needs a decision_ |
| GET | `/api/v1/company-profile/invoice-content-defaults` | `getInvoiceContentDefaults` | — | _open — needs a decision_ |
| PUT | `/api/v1/company-profile/invoice-content-defaults` | `updateInvoiceContentDefaults` | — | _open — needs a decision_ |
| GET | `/api/v1/company-profile/{companyProfileId}` | `getById` | — | _open — needs a decision_ |
| PUT | `/api/v1/company-profile/{companyProfileId}` | `update` | — | _open — needs a decision_ |

### CompanySignatureController  
`src/main/java/com/AccountReceivableManagement/controller/company_profile/CompanySignatureController.java` — 4 endpoint(s), 4 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/company-profile/signature-assets/{signatureReference}` | `image` | `@AnyFinanceRole` | docs/authorized-signature.md §2: any verified token (tightened to finance roles) |
| DELETE | `/api/v1/company-profile/{companyProfileId}/signature` | `remove` | `@ExecutiveOnly` | docs/authorized-signature.md §2: requires FINANCE_EXECUTIVE |
| GET | `/api/v1/company-profile/{companyProfileId}/signature` | `get` | `@AnyFinanceRole` | docs/authorized-signature.md §2: any verified token (tightened to finance roles) |
| PUT | `/api/v1/company-profile/{companyProfileId}/signature` | `upload` | `@ExecutiveOnly` | docs/authorized-signature.md §2: requires FINANCE_EXECUTIVE |

### CurrencyMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/CurrencyMasterController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/currency` | `getAllCurrencies` | — | _open — needs a decision_ |
| POST | `/api/currency` | `createCurrency` | — | _open — needs a decision_ |
| GET | `/api/currency/active` | `getActiveCurrencies` | — | _open — needs a decision_ |
| DELETE | `/api/currency/{currencyId}` | `deleteCurrency` | — | _open — needs a decision_ |
| GET | `/api/currency/{currencyId}` | `getCurrencyById` | — | _open — needs a decision_ |
| PUT | `/api/currency/{currencyId}` | `updateCurrency` | — | _open — needs a decision_ |

### ExchangeRateController  
`src/main/java/com/AccountReceivableManagement/controller/currency/ExchangeRateController.java` — 5 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/exchange-rates` | `getExchangeRates` | — | _open — needs a decision_ |
| POST | `/api/v1/exchange-rates` | `createSingle` | — | _open — needs a decision_ |
| POST | `/api/v1/exchange-rates/batch` | `createBatch` | — | _open — needs a decision_ |
| GET | `/api/v1/exchange-rates/lookup` | `lookup` | — | _open — needs a decision_ |
| GET | `/api/v1/exchange-rates/{id}` | `getById` | — | _open — needs a decision_ |

### HealthController  
`src/main/java/com/AccountReceivableManagement/controller/health/HealthController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/health` | `health` | — | _open — needs a decision_ |

### InvoiceApprovalController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_generation/InvoiceApprovalController.java` — 11 endpoint(s), 4 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/invoices/approval-workspace` | `getApprovalWorkspaceInvoices` | — | _open — needs a decision_ |
| GET | `/api/v1/invoices/pending-approval` | `getPendingApprovalInvoices` | — | _open — needs a decision_ |
| GET | `/api/v1/invoices/{invoiceId}/approval-history` | `getApprovalHistory` | — | _open — needs a decision_ |
| POST | `/api/v1/invoices/{invoiceId}/approve` | `approve` | `@ManagerOnly` | UAT AUTH-1 (:63); ownership table (:180); API contract §5 "Finance Manager → E2 approve" |
| GET | `/api/v1/invoices/{invoiceId}/document` | `downloadDocument` | `@AnyFinanceRole` | API contract §5: E4 listed under "Anyone" |
| POST | `/api/v1/invoices/{invoiceId}/document` | `generateDocument` | `@ManagerOnly` | API contract :65 — E3 requires INVOICE_APPROVAL, the same authority as E2 |
| POST | `/api/v1/invoices/{invoiceId}/financial-correction/reacquire` | `reacquireForFinancialCorrection` | — | _open — needs a decision_ |
| PATCH | `/api/v1/invoices/{invoiceId}/non-financial-correction` | `correctNonFinancialFields` | — | _open — needs a decision_ |
| POST | `/api/v1/invoices/{invoiceId}/refresh-after-correction` | `refreshAfterCorrection` | — | _open — needs a decision_ |
| POST | `/api/v1/invoices/{invoiceId}/reject` | `reject` | `@ManagerOnly` | UAT ownership table (:180) "Approve / reject invoices (Finance Manager)" |
| POST | `/api/v1/invoices/{invoiceId}/submit-for-approval` | `submitForApproval` | — | _open — needs a decision_ |

### InvoiceController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_generation/InvoiceController.java` — 2 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice` | `getInvoice` | — | _open — needs a decision_ |
| POST | `/api/v1/billing-snapshots/{snapshotId}/invoice` | `generateInvoice` | — | _open — needs a decision_ |

### InvoiceDeliveryController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_generation/InvoiceDeliveryController.java` — 2 endpoint(s), 1 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/invoices/{invoiceId}/deliveries` | `getDeliveryHistory` | `@AnyFinanceRole` | API contract §5: E5 listed under "Anyone" |
| POST | `/api/v1/invoices/{invoiceId}/send` | `sendInvoice` | — | API contract §5 assigns this to "Sender" — an actor that is neither of the two AR roles. Backend authority: AR_AUTH_INVOICE_DELIVERY. |

### InvoiceGenerationWorkspaceController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_generation/InvoiceGenerationWorkspaceController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/invoice-generation/workspace` | `getWorkspace` | — | _open — needs a decision_ |

### InvoiceListController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_generation/InvoiceListController.java` — 2 endpoint(s), 1 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/invoices` | `getAllInvoices` | — | _open — needs a decision_ |
| GET | `/api/v1/invoices/{invoiceId}` | `getInvoiceById` | `@AnyFinanceRole` | API contract §5: E1 listed under "Anyone" |

### InvoicePreviewController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_generation/InvoicePreviewController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice-preview` | `previewInvoice` | — | _open — needs a decision_ |

### InvoiceRecoveryController  
`src/main/java/com/AccountReceivableManagement/controller/recovery/InvoiceRecoveryController.java` — 3 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| POST | `/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax` | `recalculateStaleTax` | — | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| POST | `/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` | `resolveDelivery` | — | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |
| POST | `/api/v1/recovery/invoices/{invoiceId}/receivable` | `recoverReceivable` | — | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY |

### InvoiceSoftwareSelectionController  
`src/main/java/com/AccountReceivableManagement/controller/invoice_software_selection/InvoiceSoftwareSelectionController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/invoice/software-selection/projects/{projectId}` | `getSelectableAssets` | — | _open — needs a decision_ |

### OperationsReportController  
`src/main/java/com/AccountReceivableManagement/controller/operations/OperationsReportController.java` — 3 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/operations/alerts` | `getAlerts` | — | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data |
| GET | `/api/v1/operations/billing-summary` | `getBillingSummary` | — | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data |
| GET | `/api/v1/operations/reconciliation` | `getReconciliation` | — | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data |

### PaymentTermsMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/PaymentTermsMasterController.java` — 7 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/payment-terms` | `getAllPaymentTerms` | — | _open — needs a decision_ |
| POST | `/api/payment-terms` | `createPaymentTerm` | — | _open — needs a decision_ |
| GET | `/api/payment-terms/active` | `getActivePaymentTerms` | — | _open — needs a decision_ |
| DELETE | `/api/payment-terms/{paymentTermId}` | `deletePaymentTerm` | — | _open — needs a decision_ |
| GET | `/api/payment-terms/{paymentTermId}` | `getPaymentTermById` | — | _open — needs a decision_ |
| PUT | `/api/payment-terms/{paymentTermId}` | `updatePaymentTerm` | — | _open — needs a decision_ |
| PUT | `/api/payment-terms/{paymentTermId}/activate` | `activatePaymentTerm` | — | _open — needs a decision_ |

### ProjectMilestoneController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/ProjectMilestoneController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/projects/{pmsProjectId}/milestones` | `getMilestones` | — | _open — needs a decision_ |

### ProjectToolAssignmentController  
`src/main/java/com/AccountReceivableManagement/controller/project_tool_assignment/ProjectToolAssignmentController.java` — 7 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/project-tool-assignments` | `getAll` | — | _open — needs a decision_ |
| POST | `/api/project-tool-assignments` | `create` | — | _open — needs a decision_ |
| GET | `/api/project-tool-assignments/project/{projectId}` | `getByProject` | — | _open — needs a decision_ |
| DELETE | `/api/project-tool-assignments/{id}` | `delete` | — | _open — needs a decision_ |
| GET | `/api/project-tool-assignments/{id}` | `getById` | — | _open — needs a decision_ |
| PUT | `/api/project-tool-assignments/{id}` | `update` | — | _open — needs a decision_ |
| POST | `/api/project-tool-assignments/{id}/renew` | `renew` | — | _open — needs a decision_ |

### ProjectToolBillingConfigController  
`src/main/java/com/AccountReceivableManagement/controller/project_tool_billing/ProjectToolBillingConfigController.java` — 3 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/ar/projects/{projectId}/tool-billing` | `getToolBillingConfig` | — | _open — needs a decision_ |
| POST | `/api/ar/projects/{projectId}/tool-billing` | `createToolBillingConfig` | — | _open — needs a decision_ |
| PUT | `/api/ar/projects/{projectId}/tool-billing` | `updateToolBillingConfig` | — | _open — needs a decision_ |

### ProrationRuleMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/ProrationRuleMasterController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/proration-rule` | `getAllProrationRules` | — | _open — needs a decision_ |
| POST | `/api/proration-rule` | `createProrationRule` | — | _open — needs a decision_ |
| GET | `/api/proration-rule/active` | `getActiveProrationRules` | — | _open — needs a decision_ |
| DELETE | `/api/proration-rule/{prorationRuleId}` | `deleteProrationRule` | — | _open — needs a decision_ |
| GET | `/api/proration-rule/{prorationRuleId}` | `getProrationRuleById` | — | _open — needs a decision_ |
| PUT | `/api/proration-rule/{prorationRuleId}` | `updateProrationRule` | — | _open — needs a decision_ |

### ReceivableController  
`src/main/java/com/AccountReceivableManagement/controller/receivable/ReceivableController.java` — 2 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/receivables/outstanding` | `getOutstandingReceivables` | — | _open — needs a decision_ |
| GET | `/api/v1/receivables/{receivableId}` | `getReceivableById` | — | _open — needs a decision_ |

### RecordActionLockController  
`src/main/java/com/AccountReceivableManagement/controller/concurrency_approval/RecordActionLockController.java` — 4 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| DELETE | `/api/record-locks/{resourceType}/{resourceId}` | `releaseLock` | — | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |
| GET | `/api/record-locks/{resourceType}/{resourceId}` | `getLockStatus` | — | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |
| POST | `/api/record-locks/{resourceType}/{resourceId}` | `acquireLock` | — | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |
| PUT | `/api/record-locks/{resourceType}/{resourceId}/heartbeat` | `heartbeat` | — | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE |

### RmsAssetIntegrationController  
`src/main/java/com/AccountReceivableManagement/controller/rms_assets/RmsAssetIntegrationController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/rms-assets/projects/{projectId}/billable-assets` | `getBillableAssets` | — | _open — needs a decision_ |

### SoftwareBillingHistoryController  
`src/main/java/com/AccountReceivableManagement/controller/software_billing_history/SoftwareBillingHistoryController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/software-billing-history/assets/{assetId}` | `getHistoryForAsset` | — | _open — needs a decision_ |

### SoftwareChargeGenerationController  
`src/main/java/com/AccountReceivableManagement/controller/software_charge_generation/SoftwareChargeGenerationController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| POST | `/api/invoice/software-charge-generation` | `generateChargeLines` | — | _open — needs a decision_ |

### TaxCalculationController  
`src/main/java/com/AccountReceivableManagement/controller/tax_calculation/TaxCalculationController.java` — 2 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | `getTaxCalculation` | — | _open — needs a decision_ |
| POST | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | `calculateTax` | — | _open — needs a decision_ |

### TaxComponentMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/TaxComponentMasterController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tax-component-master` | `getAllTaxComponents` | — | _open — needs a decision_ |
| POST | `/api/tax-component-master` | `createTaxComponent` | — | _open — needs a decision_ |
| GET | `/api/tax-component-master/regime/{taxRegimeId}` | `getTaxComponentsByRegime` | — | _open — needs a decision_ |
| GET | `/api/tax-component-master/{taxComponentId}` | `getTaxComponentById` | — | _open — needs a decision_ |
| PUT | `/api/tax-component-master/{taxComponentId}` | `updateTaxComponent` | — | _open — needs a decision_ |
| PATCH | `/api/tax-component-master/{taxComponentId}/deactivate` | `deactivateTaxComponent` | — | _open — needs a decision_ |

### TaxConfigurationComponentController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/TaxConfigurationComponentController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tax-configuration-components` | `getAllComponents` | — | _open — needs a decision_ |
| GET | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | `getComponentsByConfiguration` | — | _open — needs a decision_ |
| POST | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | `createComponent` | — | _open — needs a decision_ |
| GET | `/api/tax-configuration-components/{taxConfigurationComponentId}` | `getComponentById` | — | _open — needs a decision_ |
| PUT | `/api/tax-configuration-components/{taxConfigurationComponentId}` | `updateComponent` | — | _open — needs a decision_ |
| PATCH | `/api/tax-configuration-components/{taxConfigurationComponentId}/deactivate` | `deactivateComponent` | — | _open — needs a decision_ |

### TaxConfigurationController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/TaxConfigurationController.java` — 7 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/v1/tax-rate-configurations` | `getAll` | — | _open — needs a decision_ |
| POST | `/api/v1/tax-rate-configurations` | `create` | — | _open — needs a decision_ |
| GET | `/api/v1/tax-rate-configurations/active` | `getActive` | — | _open — needs a decision_ |
| GET | `/api/v1/tax-rate-configurations/tax-region/{taxRegionId}` | `getByTaxRegion` | — | _open — needs a decision_ |
| GET | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | `getById` | — | _open — needs a decision_ |
| PUT | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | `update` | — | _open — needs a decision_ |
| PATCH | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}/deactivate` | `deactivate` | — | _open — needs a decision_ |

### TaxRegimeMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/TaxRegimeMasterController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tax-regime-master` | `getAllTaxRegimes` | — | _open — needs a decision_ |
| POST | `/api/tax-regime-master` | `createTaxRegime` | — | _open — needs a decision_ |
| GET | `/api/tax-regime-master/active` | `getActiveTaxRegimes` | — | _open — needs a decision_ |
| GET | `/api/tax-regime-master/{taxRegimeId}` | `getTaxRegimeById` | — | _open — needs a decision_ |
| PUT | `/api/tax-regime-master/{taxRegimeId}` | `updateTaxRegime` | — | _open — needs a decision_ |
| PATCH | `/api/tax-regime-master/{taxRegimeId}/deactivate` | `deactivateTaxRegime` | — | _open — needs a decision_ |

### TaxRegionMasterController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/TaxRegionMasterController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tax-region` | `getAll` | — | _open — needs a decision_ |
| POST | `/api/tax-region` | `create` | — | _open — needs a decision_ |
| GET | `/api/tax-region/active` | `getActive` | — | _open — needs a decision_ |
| GET | `/api/tax-region/{taxRegionId}` | `getById` | — | _open — needs a decision_ |
| PUT | `/api/tax-region/{taxRegionId}` | `update` | — | _open — needs a decision_ |
| PATCH | `/api/tax-region/{taxRegionId}/deactivate` | `deactivate` | — | _open — needs a decision_ |

### TaxStructureController  
`src/main/java/com/AccountReceivableManagement/controller/projectbilling_config/TaxStructureController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tax-structure/regions/{taxRegionId}` | `getTaxStructureByRegion` | — | _open — needs a decision_ |

### TaxTypeMasterController  
`src/main/java/com/AccountReceivableManagement/controller/tax_calculation/TaxTypeMasterController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tax-type-master` | `getAllTaxTypes` | — | _open — needs a decision_ |
| POST | `/api/tax-type-master` | `createTaxType` | — | _open — needs a decision_ |
| GET | `/api/tax-type-master/active` | `getActiveTaxTypes` | — | _open — needs a decision_ |
| GET | `/api/tax-type-master/{taxTypeId}` | `getTaxTypeById` | — | _open — needs a decision_ |
| PUT | `/api/tax-type-master/{taxTypeId}` | `updateTaxType` | — | _open — needs a decision_ |
| PATCH | `/api/tax-type-master/{taxTypeId}/deactivate` | `deactivateTaxType` | — | _open — needs a decision_ |

### ToolCatalogController  
`src/main/java/com/AccountReceivableManagement/controller/tool_catalog/ToolCatalogController.java` — 6 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| GET | `/api/tool-catalog` | `getAll` | — | _open — needs a decision_ |
| POST | `/api/tool-catalog` | `create` | — | _open — needs a decision_ |
| GET | `/api/tool-catalog/active` | `getActive` | — | _open — needs a decision_ |
| DELETE | `/api/tool-catalog/{toolId}` | `delete` | — | _open — needs a decision_ |
| GET | `/api/tool-catalog/{toolId}` | `getById` | — | _open — needs a decision_ |
| PUT | `/api/tool-catalog/{toolId}` | `update` | — | _open — needs a decision_ |

### ToolChargeAcquisitionController  
`src/main/java/com/AccountReceivableManagement/controller/tool_charge_acquisition/ToolChargeAcquisitionController.java` — 1 endpoint(s), 0 decided

| Method | Path | Handler | Access | Evidence / status |
|---|---|---|---|---|
| POST | `/api/tool-charge-acquisition/preview` | `preview` | — | _open — needs a decision_ |
