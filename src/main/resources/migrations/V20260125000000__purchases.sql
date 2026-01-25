CREATE TABLE purchases (
    id BIGSERIAL PRIMARY KEY,
    delivery_places TEXT,
    max_price DECIMAL(19, 2),
    updated_at TIMESTAMP,
    object_info TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
