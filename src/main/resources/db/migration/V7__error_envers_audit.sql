CREATE SEQUENCE IF NOT EXISTS revinfo_seq
    INCREMENT BY 50
    START WITH 1;

CREATE TABLE IF NOT EXISTS revinfo (
    rev      BIGINT NOT NULL DEFAULT nextval('revinfo_seq'),
    revtstmp BIGINT NOT NULL,
    actor    JSONB  NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);

ALTER TABLE error
    ADD COLUMN created_at       TIMESTAMPTZ NULL,
    ADD COLUMN created_by       JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb,
    ADD COLUMN last_modified_at TIMESTAMPTZ NULL,
    ADD COLUMN last_modified_by JSONB NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb;

-- args (ElementCollection) er @NotAudited for å unngå at PII (error_args.value) havner i
-- historikk-tabellen — det ville gjort scrubbing-jobben virkningsløs.
-- audit-kolonnene (created_*, last_modified_*) er @NotAudited fra biblioteket — aktøren bak
-- hver revisjon ligger allerede i revinfo.actor.
CREATE TABLE error_aud (
    id         BIGINT   NOT NULL,
    rev        BIGINT   NOT NULL REFERENCES revinfo(rev),
    revtype    SMALLINT,
    error_code VARCHAR(255),
    PRIMARY KEY (id, rev)
);
