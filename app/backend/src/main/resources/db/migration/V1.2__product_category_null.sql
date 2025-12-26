CREATE SCHEMA IF NOT EXISTS app;
SET search_path = app;

-- Products
ALTER TABLE product
    ALTER COLUMN category_id DROP NOT NULL;
