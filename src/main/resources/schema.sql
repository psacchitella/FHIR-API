-- FHIR Claim Processing API — PostgreSQL Schema

CREATE TABLE IF NOT EXISTS patients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_name     VARCHAR(100) NOT NULL,
    given_name      VARCHAR(100) NOT NULL,
    mrn             VARCHAR(50)  NOT NULL UNIQUE,
    birth_date      DATE,
    gender          VARCHAR(10),
    phone           VARCHAR(30),
    email           VARCHAR(150),
    address_line    VARCHAR(200),
    city            VARCHAR(100),
    state           VARCHAR(50),
    postal_code     VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS coverages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id      UUID         NOT NULL REFERENCES patients(id),
    subscriber_id   VARCHAR(100) NOT NULL,
    payor_name      VARCHAR(200) NOT NULL,
    payor_id        VARCHAR(100) NOT NULL,
    plan_name       VARCHAR(200) NOT NULL,
    group_number    VARCHAR(50),
    status          VARCHAR(20)  NOT NULL DEFAULT 'active',
    relationship    VARCHAR(20)  NOT NULL DEFAULT 'self',
    period_start    DATE,
    period_end      DATE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS claims (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id              UUID         NOT NULL REFERENCES patients(id),
    coverage_id             UUID         NOT NULL REFERENCES coverages(id),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'draft',
    type                    VARCHAR(20)  NOT NULL,
    use                     VARCHAR(20)  NOT NULL DEFAULT 'claim',
    billable_period_start   DATE         NOT NULL,
    billable_period_end     DATE         NOT NULL,
    total_amount            NUMERIC(10,2) NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'USD',
    provider_reference      VARCHAR(200),
    facility_reference      VARCHAR(200),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS claim_items (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id                    UUID         NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    sequence                    INTEGER      NOT NULL,
    product_or_service_code     VARCHAR(20)  NOT NULL,
    product_or_service_display  VARCHAR(200),
    serviced_date               DATE,
    quantity                    INTEGER      NOT NULL DEFAULT 1,
    unit_price                  NUMERIC(10,2) NOT NULL,
    net_amount                  NUMERIC(10,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS explanation_of_benefits (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id                UUID         NOT NULL UNIQUE REFERENCES claims(id),
    patient_id              UUID         NOT NULL REFERENCES patients(id),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'active',
    outcome                 VARCHAR(30)  NOT NULL,
    type                    VARCHAR(20)  NOT NULL,
    total_submitted         NUMERIC(10,2),
    total_benefit           NUMERIC(10,2),
    patient_responsibility  NUMERIC(10,2),
    deductible_applied      NUMERIC(10,2),
    coinsurance_amount      NUMERIC(10,2),
    copay_amount            NUMERIC(10,2),
    disposition             VARCHAR(500),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_coverages_patient    ON coverages(patient_id);
CREATE INDEX IF NOT EXISTS idx_coverages_status     ON coverages(patient_id, status);
CREATE INDEX IF NOT EXISTS idx_claims_patient       ON claims(patient_id);
CREATE INDEX IF NOT EXISTS idx_claims_status        ON claims(status);
CREATE INDEX IF NOT EXISTS idx_claim_items_claim    ON claim_items(claim_id);
CREATE INDEX IF NOT EXISTS idx_eob_patient          ON explanation_of_benefits(patient_id);
CREATE INDEX IF NOT EXISTS idx_eob_claim            ON explanation_of_benefits(claim_id);
