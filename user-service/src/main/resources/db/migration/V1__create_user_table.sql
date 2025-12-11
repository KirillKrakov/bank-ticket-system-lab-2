CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_CLIENT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0
);

COMMENT ON TABLE app_user IS 'Пользователи системы';
COMMENT ON COLUMN app_user.id IS 'Уникальный идентификатор пользователя';
COMMENT ON COLUMN app_user.username IS 'Логин пользователя';
COMMENT ON COLUMN app_user.email IS 'Email пользователя';
COMMENT ON COLUMN app_user.password_hash IS 'Хеш пароля';
COMMENT ON COLUMN app_user.role IS 'Роль пользователя';
COMMENT ON COLUMN app_user.created_at IS 'Дата создания';
COMMENT ON COLUMN app_user.updated_at IS 'Дата последнего обновления';
COMMENT ON COLUMN app_user.version IS 'Версия для оптимистичной блокировки';