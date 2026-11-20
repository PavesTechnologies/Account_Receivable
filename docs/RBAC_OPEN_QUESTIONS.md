# AR RBAC — open access decisions

Every endpoint whose access rule is **not** recorded anywhere in this repository.

**195 of 205 endpoints are undecided.** 16 of them are sensitive.


## Why this file exists

Three annotations now exist (`@ExecutiveOnly`, `@ManagerOnly`, `@AnyFinanceRole`)
and the ten endpoints that a document actually decides are annotated. The rest
are blank, because the repository records no role for them —
`BUSINESS_DECISIONS.md` **BD-09 ("UMS roles/permissions and JWT audience")** is
still open, and it is the decision these endpoints are waiting on.

Nothing here can be settled by reading the code. Each needs a person who owns
the finance process to say which role may call it.

## Current risk while these stay blank

An undecided endpoint keeps its existing rule: `/api/**` requires only that the
caller is authenticated. Any holder of a valid UMS token — including someone
with no finance role at all — can reach it. That is the gap UAT-D1 recorded,
and it stays open for these endpoints until each row below is answered.


## 1. Blocking question

**Who sends an invoice to the client?**

`POST /api/v1/invoices/{invoiceId}/send` — `AR_BACKEND_API_CONTRACT.md` §5
assigns this to **"Sender"**, an actor that is neither Finance_Executive nor
Finance_Manager. It is the one documented role in the invoice lifecycle with no
corresponding UMS role.

Options: Executive sends · Manager sends · either sends · a third UMS role is
needed. Sending is irreversible and reaches the client, so this should not be
left blank for long.


## 2. Sensitive endpoints awaiting a role


**AutomaticBillingRunRecoveryController**

| Method | Path | Why it matters | Decision |
|---|---|---|---|
| GET | `/api/v1/recovery/automatic-billing-runs/{runId}/events` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY | _______ |
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/reconcile-approval` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY | _______ |
| POST | `/api/v1/recovery/automatic-billing-runs/{runId}/requeue` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY | _______ |

**BillingConfigurationController**

| Method | Path | Why it matters | Decision |
|---|---|---|---|
| PUT | `/api/billing-configurations/{billingConfigurationId}/approve` | Approval action — AR_AUTH_CONFIGURATION_APPROVAL | _______ |
| PUT | `/api/billing-configurations/{billingConfigurationId}/reject` | Rejection action — AR_AUTH_CONFIGURATION_APPROVAL | _______ |

**InvoiceDeliveryController**

| Method | Path | Why it matters | Decision |
|---|---|---|---|
| POST | `/api/v1/invoices/{invoiceId}/send` | API contract §5 assigns this to "Sender" — an actor that is neither of the two AR roles. Backend authority: AR_AUTH_INVOICE_DELIVERY. | _______ |

**InvoiceRecoveryController**

| Method | Path | Why it matters | Decision |
|---|---|---|---|
| POST | `/api/v1/recovery/billing-occurrences/{occurrenceId}/recalculate-tax` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY | _______ |
| POST | `/api/v1/recovery/invoice-deliveries/{deliveryId}/resolve` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY | _______ |
| POST | `/api/v1/recovery/invoices/{invoiceId}/receivable` | Operational recovery — AR_AUTH_OPERATIONS_RECOVERY | _______ |

**OperationsReportController**

| Method | Path | Why it matters | Decision |
|---|---|---|---|
| GET | `/api/v1/operations/alerts` | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data | _______ |
| GET | `/api/v1/operations/billing-summary` | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data | _______ |
| GET | `/api/v1/operations/reconciliation` | Operations reports — AR_OPERATIONS_REPORT_AUTHORITIES; carries client + financial data | _______ |

**RecordActionLockController**

| Method | Path | Why it matters | Decision |
|---|---|---|---|
| POST | `/api/record-locks/{resourceType}/{resourceId}` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE | _______ |
| GET | `/api/record-locks/{resourceType}/{resourceId}` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE | _______ |
| DELETE | `/api/record-locks/{resourceType}/{resourceId}` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE | _______ |
| PUT | `/api/record-locks/{resourceType}/{resourceId}/heartbeat` | Generic record lock ({resourceType}); API contract §5 names Finance Manager only for INVOICE | _______ |


### A note on the record lock

`/api/record-locks/{resourceType}/{resourceId}` is **generic** — the resource
type is a path variable, so the same four endpoints lock invoices *and* billing
configurations. `AR_BACKEND_API_CONTRACT.md` §5 names Finance Manager only for
`INVOICE`. Annotating the endpoint `@ManagerOnly` would therefore also hand the
manager every other resource type, which no document supports.

If the lock needs different roles per resource type, an annotation cannot
express it and the check belongs in the service layer. Worth deciding before
anything is applied here.


## 3. Remaining endpoints awaiting a role

Mostly master data and day-to-day billing records. No document distinguishes the two roles for any of them.

Quickest path: decide per **controller**, then note exceptions.


**ArDashboardController** (2 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/dashboard/invoices` | `getInvoices` | _______ |
| GET | `/api/v1/dashboard/summary` | `getSummary` | _______ |

**BillingConfigurationController** (15 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/billing-configurations` | `getAllBillingConfigurations` | _______ |
| POST | `/api/billing-configurations` | `create` | _______ |
| GET | `/api/billing-configurations/available-projects` | `getAvailableProjectsForNewConfiguration` | _______ |
| GET | `/api/billing-configurations/clients` | `getClients` | _______ |
| POST | `/api/billing-configurations/draft` | `createDraft` | _______ |
| GET | `/api/billing-configurations/pending-approvals` | `getPendingApprovals` | _______ |
| POST | `/api/billing-configurations/preview-schedule` | `previewSchedule` | _______ |
| GET | `/api/billing-configurations/project/{projectId}` | `getApprovedByProjectId` | _______ |
| GET | `/api/billing-configurations/projects/{clientId}` | `getProjects` | _______ |
| DELETE | `/api/billing-configurations/{billingConfigurationId}` | `deleteBillingConfiguration` | _______ |
| GET | `/api/billing-configurations/{billingConfigurationId}` | `getBillingConfiguration` | _______ |
| PUT | `/api/billing-configurations/{billingConfigurationId}` | `update` | _______ |
| PATCH | `/api/billing-configurations/{billingConfigurationId}/deactivate` | `deactivateBillingConfiguration` | _______ |
| PUT | `/api/billing-configurations/{billingConfigurationId}/draft` | `saveDraft` | _______ |
| PUT | `/api/billing-configurations/{billingConfigurationId}/submit` | `submitForApproval` | _______ |

**BillingDataAcquisitionController** (2 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| POST | `/api/billing-data-acquisition/acquire` | `acquire` | _______ |
| GET | `/api/billing-data-acquisition/active-configurations` | `getActiveConfigurations` | _______ |

**BillingFixedPriceController** (5 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| DELETE | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | `delete` | _______ |
| GET | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | `get` | _______ |
| PUT | `/api/billing-fixed-price/fixed-price/{fixedPriceConfigurationId}` | `update` | _______ |
| GET | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | `getAll` | _______ |
| POST | `/api/billing-fixed-price/{billingConfigurationId}/fixed-price` | `create` | _______ |

**BillingFrequencyMasterController** (7 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/billing-frequency` | `getAllBillingFrequencies` | _______ |
| POST | `/api/billing-frequency` | `createBillingFrequency` | _______ |
| GET | `/api/billing-frequency/active` | `getActiveBillingFrequencies` | _______ |
| DELETE | `/api/billing-frequency/{billingFrequencyId}` | `deleteBillingFrequency` | _______ |
| GET | `/api/billing-frequency/{billingFrequencyId}` | `getBillingFrequencyById` | _______ |
| PUT | `/api/billing-frequency/{billingFrequencyId}` | `updateBillingFrequency` | _______ |
| PATCH | `/api/billing-frequency/{billingFrequencyId}/activate` | `activateBillingFrequency` | _______ |

**BillingMilestonePlanController** (5 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| DELETE | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | `delete` | _______ |
| GET | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | `get` | _______ |
| PUT | `/api/billing-milestone-plan/milestone-plan/{milestonePlanId}` | `update` | _______ |
| GET | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | `getByBillingConfiguration` | _______ |
| POST | `/api/billing-milestone-plan/{billingConfigurationId}/milestone-plan` | `create` | _______ |

**BillingOccurrenceController** (5 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/billing-occurrences` | `getOccurrences` | _______ |
| GET | `/api/billing-occurrences/{occurrenceId}` | `getOccurrence` | _______ |
| POST | `/api/billing-occurrences/{occurrenceId}/calculate-tax` | `calculateTax` | _______ |
| POST | `/api/billing-occurrences/{occurrenceId}/invoice` | `generateInvoice` | _______ |
| GET | `/api/billing-occurrences/{occurrenceId}/tax-calculation` | `getTaxCalculation` | _______ |

**BillingRecurringController** (8 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/billing-recurring/billing-configuration/{billingConfigurationId}` | `getByBillingConfiguration` | _______ |
| POST | `/api/billing-recurring/{billingConfigurationId}` | `create` | _______ |
| DELETE | `/api/billing-recurring/{recurringConfigurationId}` | `delete` | _______ |
| GET | `/api/billing-recurring/{recurringConfigurationId}` | `get` | _______ |
| PUT | `/api/billing-recurring/{recurringConfigurationId}` | `update` | _______ |
| POST | `/api/billing-recurring/{recurringConfigurationId}/renew` | `renew` | _______ |
| GET | `/api/billing-recurring/{recurringConfigurationId}/renewal-history` | `getRenewalHistory` | _______ |
| GET | `/api/billing-recurring/{recurringConfigurationId}/schedule` | `getBillingSchedule` | _______ |

**BillingSnapshotController** (3 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/billing-snapshots` | `getByBillingConfiguration` | _______ |
| POST | `/api/v1/billing-snapshots` | `createBillingSnapshot` | _______ |
| GET | `/api/v1/billing-snapshots/by-period` | `getByProjectAndPeriod` | _______ |

**BillingTMRateCardController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| DELETE | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | `deleteRateCard` | _______ |
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | `getRateCard` | _______ |
| PUT | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}` | `updateRateCard` | _______ |
| GET | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | `getAllRateCards` | _______ |
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards` | `addRateCard` | _______ |
| POST | `/api/billing-tm-rate-card/{billingConfigurationId}/tm-rate-cards/save` | `saveRateCard` | _______ |

**BillingTMRateDiscountController** (5 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | `getDiscounts` | _______ |
| POST | `/api/billing-tm-rate-card/tm-rate-cards/{rateCardId}/discounts` | `addDiscount` | _______ |
| DELETE | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | `deleteDiscount` | _______ |
| GET | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | `getDiscount` | _______ |
| PUT | `/api/billing-tm-rate-card/tm-rate-discounts/{discountId}` | `updateDiscount` | _______ |

**BillingTypeMasterController** (7 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/billing-types` | `getAllBillingTypes` | _______ |
| POST | `/api/billing-types` | `createBillingType` | _______ |
| GET | `/api/billing-types/active` | `getActiveBillingTypes` | _______ |
| DELETE | `/api/billing-types/{billingTypeId}` | `deleteBillingType` | _______ |
| GET | `/api/billing-types/{billingTypeId}` | `getBillingTypeById` | _______ |
| PUT | `/api/billing-types/{billingTypeId}` | `updateBillingType` | _______ |
| PATCH | `/api/billing-types/{billingTypeId}/activate` | `activateBillingType` | _______ |

**ClientBudgetSummaryController** (2 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| POST | `/client-budget-summary/refresh/{clientId}` | `refreshBudget` | _______ |
| GET | `/client-budget-summary/{clientId}` | `getClientBudget` | _______ |

**CompanyProfileController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/company-profile` | `getActive` | _______ |
| POST | `/api/v1/company-profile` | `create` | _______ |
| GET | `/api/v1/company-profile/invoice-content-defaults` | `getInvoiceContentDefaults` | _______ |
| PUT | `/api/v1/company-profile/invoice-content-defaults` | `updateInvoiceContentDefaults` | _______ |
| GET | `/api/v1/company-profile/{companyProfileId}` | `getById` | _______ |
| PUT | `/api/v1/company-profile/{companyProfileId}` | `update` | _______ |

**CurrencyMasterController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/currency` | `getAllCurrencies` | _______ |
| POST | `/api/currency` | `createCurrency` | _______ |
| GET | `/api/currency/active` | `getActiveCurrencies` | _______ |
| DELETE | `/api/currency/{currencyId}` | `deleteCurrency` | _______ |
| GET | `/api/currency/{currencyId}` | `getCurrencyById` | _______ |
| PUT | `/api/currency/{currencyId}` | `updateCurrency` | _______ |

**ExchangeRateController** (5 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/exchange-rates` | `getExchangeRates` | _______ |
| POST | `/api/v1/exchange-rates` | `createSingle` | _______ |
| POST | `/api/v1/exchange-rates/batch` | `createBatch` | _______ |
| GET | `/api/v1/exchange-rates/lookup` | `lookup` | _______ |
| GET | `/api/v1/exchange-rates/{id}` | `getById` | _______ |

**HealthController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/health` | `health` | _______ |

**InvoiceApprovalController** (7 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/invoices/approval-workspace` | `getApprovalWorkspaceInvoices` | _______ |
| GET | `/api/v1/invoices/pending-approval` | `getPendingApprovalInvoices` | _______ |
| GET | `/api/v1/invoices/{invoiceId}/approval-history` | `getApprovalHistory` | _______ |
| POST | `/api/v1/invoices/{invoiceId}/financial-correction/reacquire` | `reacquireForFinancialCorrection` | _______ |
| PATCH | `/api/v1/invoices/{invoiceId}/non-financial-correction` | `correctNonFinancialFields` | _______ |
| POST | `/api/v1/invoices/{invoiceId}/refresh-after-correction` | `refreshAfterCorrection` | _______ |
| POST | `/api/v1/invoices/{invoiceId}/submit-for-approval` | `submitForApproval` | _______ |

**InvoiceController** (2 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice` | `getInvoice` | _______ |
| POST | `/api/v1/billing-snapshots/{snapshotId}/invoice` | `generateInvoice` | _______ |

**InvoiceGenerationWorkspaceController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/invoice-generation/workspace` | `getWorkspace` | _______ |

**InvoiceListController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/invoices` | `getAllInvoices` | _______ |

**InvoicePreviewController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/invoice-preview` | `previewInvoice` | _______ |

**InvoiceSoftwareSelectionController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/invoice/software-selection/projects/{projectId}` | `getSelectableAssets` | _______ |

**PaymentTermsMasterController** (7 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/payment-terms` | `getAllPaymentTerms` | _______ |
| POST | `/api/payment-terms` | `createPaymentTerm` | _______ |
| GET | `/api/payment-terms/active` | `getActivePaymentTerms` | _______ |
| DELETE | `/api/payment-terms/{paymentTermId}` | `deletePaymentTerm` | _______ |
| GET | `/api/payment-terms/{paymentTermId}` | `getPaymentTermById` | _______ |
| PUT | `/api/payment-terms/{paymentTermId}` | `updatePaymentTerm` | _______ |
| PUT | `/api/payment-terms/{paymentTermId}/activate` | `activatePaymentTerm` | _______ |

**ProjectMilestoneController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/projects/{pmsProjectId}/milestones` | `getMilestones` | _______ |

**ProjectToolAssignmentController** (7 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/project-tool-assignments` | `getAll` | _______ |
| POST | `/api/project-tool-assignments` | `create` | _______ |
| GET | `/api/project-tool-assignments/project/{projectId}` | `getByProject` | _______ |
| DELETE | `/api/project-tool-assignments/{id}` | `delete` | _______ |
| GET | `/api/project-tool-assignments/{id}` | `getById` | _______ |
| PUT | `/api/project-tool-assignments/{id}` | `update` | _______ |
| POST | `/api/project-tool-assignments/{id}/renew` | `renew` | _______ |

**ProjectToolBillingConfigController** (3 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/ar/projects/{projectId}/tool-billing` | `getToolBillingConfig` | _______ |
| POST | `/api/ar/projects/{projectId}/tool-billing` | `createToolBillingConfig` | _______ |
| PUT | `/api/ar/projects/{projectId}/tool-billing` | `updateToolBillingConfig` | _______ |

**ProrationRuleMasterController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/proration-rule` | `getAllProrationRules` | _______ |
| POST | `/api/proration-rule` | `createProrationRule` | _______ |
| GET | `/api/proration-rule/active` | `getActiveProrationRules` | _______ |
| DELETE | `/api/proration-rule/{prorationRuleId}` | `deleteProrationRule` | _______ |
| GET | `/api/proration-rule/{prorationRuleId}` | `getProrationRuleById` | _______ |
| PUT | `/api/proration-rule/{prorationRuleId}` | `updateProrationRule` | _______ |

**ReceivableController** (2 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/receivables/outstanding` | `getOutstandingReceivables` | _______ |
| GET | `/api/v1/receivables/{receivableId}` | `getReceivableById` | _______ |

**RmsAssetIntegrationController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/rms-assets/projects/{projectId}/billable-assets` | `getBillableAssets` | _______ |

**SoftwareBillingHistoryController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/software-billing-history/assets/{assetId}` | `getHistoryForAsset` | _______ |

**SoftwareChargeGenerationController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| POST | `/api/invoice/software-charge-generation` | `generateChargeLines` | _______ |

**TaxCalculationController** (2 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | `getTaxCalculation` | _______ |
| POST | `/api/v1/billing-snapshots/{snapshotId}/tax-calculation` | `calculateTax` | _______ |

**TaxComponentMasterController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tax-component-master` | `getAllTaxComponents` | _______ |
| POST | `/api/tax-component-master` | `createTaxComponent` | _______ |
| GET | `/api/tax-component-master/regime/{taxRegimeId}` | `getTaxComponentsByRegime` | _______ |
| GET | `/api/tax-component-master/{taxComponentId}` | `getTaxComponentById` | _______ |
| PUT | `/api/tax-component-master/{taxComponentId}` | `updateTaxComponent` | _______ |
| PATCH | `/api/tax-component-master/{taxComponentId}/deactivate` | `deactivateTaxComponent` | _______ |

**TaxConfigurationComponentController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tax-configuration-components` | `getAllComponents` | _______ |
| GET | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | `getComponentsByConfiguration` | _______ |
| POST | `/api/tax-configuration-components/tax-configuration/{taxConfigurationId}` | `createComponent` | _______ |
| GET | `/api/tax-configuration-components/{taxConfigurationComponentId}` | `getComponentById` | _______ |
| PUT | `/api/tax-configuration-components/{taxConfigurationComponentId}` | `updateComponent` | _______ |
| PATCH | `/api/tax-configuration-components/{taxConfigurationComponentId}/deactivate` | `deactivateComponent` | _______ |

**TaxConfigurationController** (7 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/v1/tax-rate-configurations` | `getAll` | _______ |
| POST | `/api/v1/tax-rate-configurations` | `create` | _______ |
| GET | `/api/v1/tax-rate-configurations/active` | `getActive` | _______ |
| GET | `/api/v1/tax-rate-configurations/tax-region/{taxRegionId}` | `getByTaxRegion` | _______ |
| GET | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | `getById` | _______ |
| PUT | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}` | `update` | _______ |
| PATCH | `/api/v1/tax-rate-configurations/{taxRateConfigurationId}/deactivate` | `deactivate` | _______ |

**TaxRegimeMasterController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tax-regime-master` | `getAllTaxRegimes` | _______ |
| POST | `/api/tax-regime-master` | `createTaxRegime` | _______ |
| GET | `/api/tax-regime-master/active` | `getActiveTaxRegimes` | _______ |
| GET | `/api/tax-regime-master/{taxRegimeId}` | `getTaxRegimeById` | _______ |
| PUT | `/api/tax-regime-master/{taxRegimeId}` | `updateTaxRegime` | _______ |
| PATCH | `/api/tax-regime-master/{taxRegimeId}/deactivate` | `deactivateTaxRegime` | _______ |

**TaxRegionMasterController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tax-region` | `getAll` | _______ |
| POST | `/api/tax-region` | `create` | _______ |
| GET | `/api/tax-region/active` | `getActive` | _______ |
| GET | `/api/tax-region/{taxRegionId}` | `getById` | _______ |
| PUT | `/api/tax-region/{taxRegionId}` | `update` | _______ |
| PATCH | `/api/tax-region/{taxRegionId}/deactivate` | `deactivate` | _______ |

**TaxStructureController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tax-structure/regions/{taxRegionId}` | `getTaxStructureByRegion` | _______ |

**TaxTypeMasterController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tax-type-master` | `getAllTaxTypes` | _______ |
| POST | `/api/tax-type-master` | `createTaxType` | _______ |
| GET | `/api/tax-type-master/active` | `getActiveTaxTypes` | _______ |
| GET | `/api/tax-type-master/{taxTypeId}` | `getTaxTypeById` | _______ |
| PUT | `/api/tax-type-master/{taxTypeId}` | `updateTaxType` | _______ |
| PATCH | `/api/tax-type-master/{taxTypeId}/deactivate` | `deactivateTaxType` | _______ |

**ToolCatalogController** (6 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| GET | `/api/tool-catalog` | `getAll` | _______ |
| POST | `/api/tool-catalog` | `create` | _______ |
| GET | `/api/tool-catalog/active` | `getActive` | _______ |
| DELETE | `/api/tool-catalog/{toolId}` | `delete` | _______ |
| GET | `/api/tool-catalog/{toolId}` | `getById` | _______ |
| PUT | `/api/tool-catalog/{toolId}` | `update` | _______ |

**ToolChargeAcquisitionController** (1 endpoint(s)) — whole-controller decision: _______

| Method | Path | Handler | Exception? |
|---|---|---|---|
| POST | `/api/tool-charge-acquisition/preview` | `preview` | _______ |


## How to fill this in

For each row write one of `@ExecutiveOnly`, `@ManagerOnly`, `@AnyFinanceRole`.

Two questions that resolve most rows quickly:

1. *Is there any reason one of the two roles must be kept out?* If not, it is
   `@AnyFinanceRole`.
2. *Would an auditor expect this to be restricted?* If yes, it is not
   `@AnyFinanceRole`, and the next question is which role.

Once a section is filled in, the annotations are a mechanical change — the
matrix rows map one-to-one onto controller methods.

