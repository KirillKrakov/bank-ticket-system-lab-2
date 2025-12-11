-- Этот скрипт выполняется при инициализации контейнера PostgreSQL
-- Гарантируем, что база данных существует
SELECT 'CREATE DATABASE userdb'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'userdb')\gexec

-- Подключаемся к созданной БД
\c userdb;

-- Создаём расширение для UUID (если ещё нет)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";