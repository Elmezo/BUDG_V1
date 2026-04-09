-- Data Store Table
CREATE TABLE IF NOT EXISTS values_datastore (
    id SERIAL PRIMARY KEY,
    dataset_id INTEGER NOT NULL UNIQUE REFERENCES dataset(id),
    -- Metadata fields
    frequency VARCHAR(50),
    frequency_comments TEXT,
    availability VARCHAR(100),
    availability_comments TEXT,
    values_in_axon BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Field Table
CREATE TABLE IF NOT EXISTS values_field (
    id SERIAL PRIMARY KEY,
    datastore_id INTEGER NOT NULL REFERENCES values_datastore(id),
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(50), -- e.g. String, Integer, etc.
    dataset_attribute_id INTEGER -- Optional link to original attribute
);

-- Rule Table
CREATE TABLE IF NOT EXISTS values_rule (
    id SERIAL PRIMARY KEY,
    field_id INTEGER NOT NULL REFERENCES values_field(id),
    rule_type VARCHAR(50) NOT NULL, -- 'MAX_LENGTH', 'PRIMARY'
    rule_value VARCHAR(255)
);

-- Entry Reference Table (Batch upload Header)
CREATE TABLE IF NOT EXISTS values_entry_ref (
    id SERIAL PRIMARY KEY,
    datastore_id INTEGER NOT NULL REFERENCES values_datastore(id),
    upload_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    row_count INTEGER,
    upload_type VARCHAR(20) -- 'APPEND', 'OVERWRITE'
);

-- Entry Table (Actual Values)
CREATE TABLE IF NOT EXISTS values_entry (
    id SERIAL PRIMARY KEY,
    entry_ref_id INTEGER NOT NULL REFERENCES values_entry_ref(id),
    field_id INTEGER NOT NULL REFERENCES values_field(id),
    row_index INTEGER, -- To group values of the same row
    value_text TEXT
);

-- Audit Table
CREATE TABLE IF NOT EXISTS values_audit (
    id SERIAL PRIMARY KEY,
    dataset_id INTEGER NOT NULL,
    action_type VARCHAR(50) NOT NULL, -- 'INSERT', 'DELETE'
    action_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    user_id INTEGER,
    details TEXT
);
