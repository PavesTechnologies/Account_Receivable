-- Epic 4 - Tool / Software / License Billing (Phase 1)
-- Reference INSERT statements for the three proration_rule_master records
-- required before a Tool Billing Configuration can be saved (NONE, DAILY, MONTHLY).
--
-- NOT executed automatically - this project has no Flyway/Liquibase and no
-- data.sql seeding mechanism (schema is `spring.jpa.hibernate.ddl-auto=update`,
-- and no other master table in this project is auto-seeded either). Run
-- manually per environment after the proration_rule_master table exists, e.g.
-- via the ProrationRuleMasterController API (POST /api/proration-rule) or by
-- executing this script directly against the database.

INSERT INTO proration_rule_master
    (proration_rule_id, proration_rule_code, proration_rule_name, description, is_active, created_at, updated_at)
VALUES
    (UUID(), 'NONE', 'None', 'No proration applied.', 1, NOW(), NOW()),
    (UUID(), 'DAILY', 'Daily', 'Charges are prorated on a daily basis.', 1, NOW(), NOW()),
    (UUID(), 'MONTHLY', 'Monthly', 'Charges are prorated on a monthly basis.', 1, NOW(), NOW());
