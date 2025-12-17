-- Таблица заявок
CREATE TABLE IF NOT EXISTS application (
    id UUID PRIMARY KEY,
    applicant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    version BIGINT DEFAULT 0
);

-- Таблица документов
CREATE TABLE IF NOT EXISTS document (
    id UUID PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255),
    storage_path VARCHAR(1000),
    application_id UUID NOT NULL,
    CONSTRAINT fk_document_application FOREIGN KEY (application_id) REFERENCES application(id) ON DELETE CASCADE
);

-- Таблица истории заявок
CREATE TABLE IF NOT EXISTS application_history (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    changed_by VARCHAR(100),
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_history_application FOREIGN KEY (application_id) REFERENCES application(id) ON DELETE CASCADE
);

-- Таблица тегов (только связь)
CREATE TABLE IF NOT EXISTS application_tag (
    application_id UUID NOT NULL,
    tag_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (application_id, tag_name),
    CONSTRAINT fk_application_tag_application FOREIGN KEY (application_id) REFERENCES application(id) ON DELETE CASCADE
);

-- Индексы
CREATE INDEX IF NOT EXISTS idx_application_applicant ON application(applicant_id);
CREATE INDEX IF NOT EXISTS idx_application_product ON application(product_id);
CREATE INDEX IF NOT EXISTS idx_application_created_at ON application(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_document_application ON document(application_id);
CREATE INDEX IF NOT EXISTS idx_history_application ON application_history(application_id);
CREATE INDEX IF NOT EXISTS idx_application_tag_application ON application_tag(application_id);