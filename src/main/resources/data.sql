INSERT INTO wallets (id, user_id, balance, created_at, updated_at)
VALUES (
    RANDOM_UUID(),
    '11111111-1111-1111-1111-111111111111',
    500.00,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);