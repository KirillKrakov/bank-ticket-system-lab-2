-- Таблица тегов
CREATE TABLE IF NOT EXISTS tag (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_tag_name ON tag(name);