CREATE TABLE customers (
    customer_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE PUBLICATION customers_pub FOR TABLE customers;

INSERT INTO customers (name, email, created_at)
VALUES 
    ('John Doe', 'john@example.com', '2024-01-01 00:00:00'),
    ('Jane Smith', 'jane@example.com', '2024-01-01 00:00:00');