CREATE TABLE IF NOT EXISTS application (
    id UUID PRIMARY KEY NOT NULL,
    applicant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE
);

--ALTER TABLE application
--ADD CONSTRAINT fk_application_applicant
--FOREIGN KEY (applicant_id)
--REFERENCES app_user (id);
--
--ALTER TABLE application
--ADD CONSTRAINT fk_application_product
--FOREIGN KEY (product_id)
--REFERENCES product (id);

-- Индексы
--CREATE INDEX IF NOT EXISTS idx_product_name ON product(name);