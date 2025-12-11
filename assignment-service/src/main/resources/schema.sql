-- Создание таблицы назначений пользователь-продукт
CREATE TABLE IF NOT EXISTS user_product_assignment (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    product_id UUID NOT NULL,
    role_on_product VARCHAR(100),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Уникальное ограничение
ALTER TABLE user_product_assignment ADD CONSTRAINT uc_user_product
    UNIQUE (user_id, product_id);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_upa_user ON user_product_assignment(user_id);
CREATE INDEX IF NOT EXISTS idx_upa_product ON user_product_assignment(product_id);
CREATE INDEX IF NOT EXISTS idx_upa_user_product ON user_product_assignment(user_id, product_id);

-- Комментарии
COMMENT ON TABLE user_product_assignment IS 'Назначения пользователей на продукты';
COMMENT ON COLUMN user_product_assignment.id IS 'Уникальный идентификатор назначения';
COMMENT ON COLUMN user_product_assignment.user_id IS 'ID пользователя';
COMMENT ON COLUMN user_product_assignment.product_id IS 'ID продукта';
COMMENT ON COLUMN user_product_assignment.role_on_product IS 'Роль пользователя на продукте';
COMMENT ON COLUMN user_product_assignment.assigned_at IS 'Дата назначения';