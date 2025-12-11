-- Индекс для быстрого поиска по username
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);

-- Индекс для быстрого поиска по email
CREATE INDEX IF NOT EXISTS idx_app_user_email ON app_user(email);

-- Индекс для сортировки по дате создания
CREATE INDEX IF NOT EXISTS idx_app_user_created_at ON app_user(created_at DESC);