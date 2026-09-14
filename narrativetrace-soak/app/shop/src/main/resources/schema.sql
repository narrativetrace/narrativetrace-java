CREATE SEQUENCE IF NOT EXISTS order_id_seq START WITH 1;

CREATE TABLE IF NOT EXISTS catalog (
    product_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    price DOUBLE NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(16) PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    transaction_id VARCHAR(32),
    subtotal DOUBLE NOT NULL,
    shipping_cost DOUBLE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS order_lines (
    order_id VARCHAR(16) NOT NULL REFERENCES orders(order_id),
    product_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL
);
