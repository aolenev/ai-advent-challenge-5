CREATE TABLE IF NOT EXISTS fueling_stat
(
    id             BIGSERIAL PRIMARY KEY,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    fueling_count  NUMERIC   NOT NULL,
    fueling_liters NUMERIC   NOT NULL,
    from_ms        BIGINT    NOT NULL,
    to_ms          BIGINT
)