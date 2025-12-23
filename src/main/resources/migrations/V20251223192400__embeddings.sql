CREATE TABLE rag_embeddings
(
    id        BIGSERIAL PRIMARY KEY,
    chunk     TEXT        NOT NULL,
    embedding VECTOR(768) NOT NULL
)