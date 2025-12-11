-- Создание таблицы продуктов
CREATE TABLE IF NOT EXISTS product (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

-- Комментарии к таблице
COMMENT ON TABLE product IS 'Банковские продукты';
COMMENT ON COLUMN product.id IS 'Уникальный идентификатор продукта';
COMMENT ON COLUMN product.name IS 'Название продукта';
COMMENT ON COLUMN product.description IS 'Описание продукта';

-- Индексы
CREATE INDEX IF NOT EXISTS idx_product_name ON product(name);