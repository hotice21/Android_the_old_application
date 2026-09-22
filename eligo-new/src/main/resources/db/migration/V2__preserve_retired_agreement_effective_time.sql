ALTER TABLE agreements
    DROP CHECK chk_agreement_effective,
    ADD CONSTRAINT chk_agreement_effective CHECK (
        (status IN (3, 4) AND effective_at IS NOT NULL)
        OR (status IN (1, 2) AND effective_at IS NULL)
    );
