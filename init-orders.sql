CREATE TABLE orders (
    order_id SERIAL PRIMARY KEY,
    customer_id INTEGER NOT NULL,
    order_date TIMESTAMP NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) NOT NULL
);

CREATE PUBLICATION orders_pub FOR TABLE orders;

INSERT INTO orders (customer_id, order_date, total_amount, status)
VALUES 
    (1, '2024-01-01 10:00:00', 100.50, 'COMPLETED'),
    (2, '2024-01-01 11:00:00', 200.75, 'PENDING'),
    (1, '2024-01-02 09:30:00', 150.25, 'PROCESSING');
