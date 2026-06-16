-- Candidates: applications. At most one row per DUNS. The `state` column tracks
-- the lifecycle (PENDING or REFUSED). A refused candidacy may transition back to
-- PENDING when the candidate re-applies (see CreateCandidateService).
CREATE TABLE candidates (
    duns            BIGINT       NOT NULL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    country         VARCHAR(2)      NOT NULL,
    annual_turnover BIGINT       NOT NULL CHECK (annual_turnover >= 0),
    state           VARCHAR(16)  NOT NULL CHECK (state IN ('PENDING','REFUSED')),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_candidates_state ON candidates (state);

CREATE TABLE suppliers (
    duns                  BIGINT       PRIMARY KEY,
    name                  VARCHAR(255) NOT NULL,
    country               VARCHAR(2)      NOT NULL,
    annual_turnover       BIGINT       NOT NULL CHECK (annual_turnover >= 0),
    sustainability_rating CHAR(1)      NOT NULL CHECK (sustainability_rating IN ('A','B','C','D','E')),
    status                VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE','ON_PROBATION','DISQUALIFIED')),
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Indices to support the potential-suppliers query at 100k–1M scale.
CREATE INDEX ix_suppliers_country_turnover ON suppliers (country, annual_turnover);
CREATE INDEX ix_suppliers_status ON suppliers (status);
CREATE INDEX ix_suppliers_turnover ON suppliers (annual_turnover);
