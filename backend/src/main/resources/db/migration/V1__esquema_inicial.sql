-- Esquema inicial de Veterinaria San Marcos.
-- Compatible con MySQL 8 (RDS) y con H2 en modo MySQL (desarrollo local).

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    user_role     VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE categories (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    category_id BIGINT       NOT NULL,
    price       INT          NOT NULL,
    stock       INT          NOT NULL,
    offer       BOOLEAN      NOT NULL DEFAULT FALSE,
    description VARCHAR(500) NOT NULL,
    emoji       VARCHAR(16)  NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE TABLE orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    customer_name  VARCHAR(120) NOT NULL,
    customer_email VARCHAR(160) NOT NULL,
    address        VARCHAR(255) NOT NULL,
    delivery       VARCHAR(60)  NOT NULL,
    payment        VARCHAR(60)  NOT NULL,
    total          BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE order_items (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    unit_price   INT          NOT NULL,
    quantity     INT          NOT NULL,
    subtotal     BIGINT       NOT NULL,
    CONSTRAINT fk_items_order   FOREIGN KEY (order_id)   REFERENCES orders (id),
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_orders_user ON orders (user_id);
CREATE INDEX idx_items_order ON order_items (order_id);
