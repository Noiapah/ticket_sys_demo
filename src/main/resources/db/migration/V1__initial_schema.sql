CREATE TABLE employees (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE,
    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
    created_at TEXT NOT NULL
);

CREATE TABLE customers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    phone_normalized TEXT NOT NULL UNIQUE,
    phone_display TEXT NOT NULL,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL REFERENCES customers(id),
    device_type TEXT NOT NULL,
    manufacturer TEXT NOT NULL DEFAULT '',
    device_model TEXT NOT NULL,
    operating_system TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    created_by INTEGER NOT NULL REFERENCES employees(id),
    assigned_to INTEGER NOT NULL REFERENCES employees(id),
    status TEXT NOT NULL CHECK (status IN ('IN_PROGRESS', 'WAITING', 'ESCALATED', 'CLOSED')),
    urgent INTEGER NOT NULL DEFAULT 0 CHECK (urgent IN (0, 1)),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    closed_at TEXT,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL REFERENCES tickets(id),
    employee_id INTEGER NOT NULL REFERENCES employees(id),
    text TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE ticket_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL REFERENCES tickets(id),
    actor_employee_id INTEGER NOT NULL REFERENCES employees(id),
    event_type TEXT NOT NULL,
    summary TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE app_settings (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL
);

CREATE INDEX idx_tickets_status_created ON tickets(status, created_at);
CREATE INDEX idx_tickets_assigned ON tickets(assigned_to);
CREATE INDEX idx_tickets_category ON tickets(category);
CREATE INDEX idx_comments_ticket ON comments(ticket_id, created_at);
CREATE INDEX idx_history_ticket ON ticket_history(ticket_id, created_at);

