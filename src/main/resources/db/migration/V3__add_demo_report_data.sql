-- A representative one-time demo dataset for testing dashboards and Excel exports.
-- Flyway records this migration, so the rows are not duplicated on later starts.

INSERT OR IGNORE INTO employees(name, active, created_at) VALUES
    ('Amalie',  1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Henrik',  1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Silje',   1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Tobias',  1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Mathilde',1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Sander',  1, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Nora',    0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    ('Eirik',   0, strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));

WITH RECURSIVE numbers(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 70
)
INSERT OR IGNORE INTO customers(phone_normalized, phone_display, name, created_at, updated_at)
SELECT
    printf('+4745%06d', n),
    printf('45 %02d %02d %02d', (n / 10000) % 100, (n / 100) % 100, n % 100),
    printf('Demokunde %03d', n),
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now'),
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
FROM numbers;

WITH RECURSIVE numbers(n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 140
), demo AS (
    SELECT
        n,
        ((n - 1) % 70) + 1 customer_number,
        CASE ((n - 1) % 8)
            WHEN 0 THEN 'Amalie' WHEN 1 THEN 'Henrik' WHEN 2 THEN 'Silje' WHEN 3 THEN 'Tobias'
            WHEN 4 THEN 'Mathilde' WHEN 5 THEN 'Sander' WHEN 6 THEN 'Nora' ELSE 'Eirik'
        END creator_name,
        CASE ((n + 1) % 8)
            WHEN 0 THEN 'Amalie' WHEN 1 THEN 'Henrik' WHEN 2 THEN 'Silje' WHEN 3 THEN 'Tobias'
            WHEN 4 THEN 'Mathilde' WHEN 5 THEN 'Sander' WHEN 6 THEN 'Nora' ELSE 'Eirik'
        END assignee_name,
        CASE ((n - 1) % 10)
            WHEN 0 THEN 'CLOSED' WHEN 1 THEN 'CLOSED' WHEN 2 THEN 'CLOSED'
            WHEN 3 THEN 'CLOSED' WHEN 4 THEN 'CLOSED' WHEN 5 THEN 'CLOSED'
            WHEN 6 THEN 'IN_PROGRESS' WHEN 7 THEN 'IN_PROGRESS' WHEN 8 THEN 'WAITING'
            ELSE 'ESCALATED'
        END ticket_status,
        CASE ((n - 1) % 8)
            WHEN 0 THEN 'Dataoverføring / sikkerhetskopi / oppsett'
            WHEN 1 THEN 'Nettverk / tilkobling'
            WHEN 2 THEN 'Konto / brukernavn / passord'
            WHEN 3 THEN 'E-post'
            WHEN 4 THEN 'Virus / skadevare'
            WHEN 5 THEN 'App-problemer'
            WHEN 6 THEN 'Systemproblemer'
            ELSE 'Annet'
        END ticket_category,
        printf('-%d minutes', (141 - n) * 35) created_modifier,
        CASE ((n - 1) % 4) WHEN 0 THEN 18 WHEN 1 THEN 45 WHEN 2 THEN 80 ELSE 150 END resolution_minutes
    FROM numbers
)
INSERT INTO tickets(
    customer_id, device_type, manufacturer, device_model, new_device_model,
    operating_system, category, description, created_by, assigned_to,
    status, urgent, created_at, updated_at, closed_at, version
)
SELECT
    (SELECT id FROM customers WHERE phone_normalized = printf('+4745%06d', customer_number)),
    CASE ((n - 1) % 8) WHEN 4 THEN 'TABLET' WHEN 5 THEN 'TABLET' WHEN 6 THEN 'SMARTWATCH' WHEN 7 THEN 'COMPUTER' ELSE 'PHONE' END,
    CASE ((n - 1) % 8) WHEN 0 THEN 'Apple' WHEN 1 THEN 'Samsung' WHEN 2 THEN 'Google' WHEN 3 THEN 'Doro' WHEN 4 THEN 'Apple' WHEN 5 THEN 'Samsung' WHEN 6 THEN 'Apple' ELSE 'Lenovo' END,
    CASE ((n - 1) % 8) WHEN 0 THEN 'iPhone 16' WHEN 1 THEN 'Samsung Galaxy S25' WHEN 2 THEN 'Google Pixel 9' WHEN 3 THEN 'Doro Smartphone' WHEN 4 THEN 'iPad Air' WHEN 5 THEN 'Samsung Galaxy Tab S10' WHEN 6 THEN 'Apple Watch Series 10' ELSE 'Lenovo IdeaPad' END,
    CASE WHEN ticket_category = 'Dataoverføring / sikkerhetskopi / oppsett' THEN 'iPhone 17' ELSE '' END,
    CASE ((n - 1) % 8) WHEN 0 THEN 'IOS' WHEN 1 THEN 'ANDROID' WHEN 2 THEN 'ANDROID' WHEN 3 THEN 'ANDROID' WHEN 4 THEN 'IOS' WHEN 5 THEN 'ANDROID' WHEN 6 THEN 'IOS' ELSE 'OTHER' END,
    ticket_category,
    CASE ((n - 1) % 8)
        WHEN 0 THEN '[DEMO] Overføre innhold og kontrollere sikkerhetskopien.'
        WHEN 1 THEN '[DEMO] Enheten mister forbindelsen til trådløst nettverk.'
        WHEN 2 THEN '[DEMO] Kunden kommer ikke inn på kontoen sin.'
        WHEN 3 THEN '[DEMO] E-post synkroniseres ikke på den nye enheten.'
        WHEN 4 THEN '[DEMO] Mistenkelig popup vises når nettleseren åpnes.'
        WHEN 5 THEN '[DEMO] Appen avsluttes under oppstart.'
        WHEN 6 THEN '[DEMO] Varslinger og lyd virker ikke som forventet.'
        ELSE '[DEMO] Generell veiledning og kontroll av innstillinger.'
    END,
    (SELECT id FROM employees WHERE name = creator_name),
    (SELECT id FROM employees WHERE name = assignee_name),
    ticket_status,
    CASE WHEN n % 11 = 0 THEN 1 ELSE 0 END,
    strftime('%Y-%m-%dT%H:%M:%fZ', 'now', created_modifier),
    CASE WHEN ticket_status = 'CLOSED'
        THEN strftime('%Y-%m-%dT%H:%M:%fZ', 'now', created_modifier, printf('+%d minutes', resolution_minutes))
        ELSE strftime('%Y-%m-%dT%H:%M:%fZ', 'now', created_modifier, '+10 minutes')
    END,
    CASE WHEN ticket_status = 'CLOSED'
        THEN strftime('%Y-%m-%dT%H:%M:%fZ', 'now', created_modifier, printf('+%d minutes', resolution_minutes))
        ELSE NULL
    END,
    2 + CASE WHEN n % 11 = 0 THEN 1 ELSE 0 END
FROM demo;

INSERT INTO ticket_history(ticket_id, actor_employee_id, event_type, summary, created_at)
SELECT id, created_by, 'CREATED', 'Saken ble opprettet', created_at
FROM tickets WHERE description LIKE '[DEMO]%';

INSERT INTO ticket_history(ticket_id, actor_employee_id, event_type, summary, created_at)
SELECT
    id,
    assigned_to,
    CASE WHEN status = 'CLOSED' THEN 'CLOSED' ELSE 'STATUS' END,
    CASE status
        WHEN 'CLOSED' THEN 'Saken ble lukket'
        WHEN 'ESCALATED' THEN 'Status satt til Eskalert'
        WHEN 'WAITING' THEN 'Status satt til Venter'
        ELSE 'Status satt til Pågår'
    END,
    updated_at
FROM tickets WHERE description LIKE '[DEMO]%';

INSERT INTO ticket_history(ticket_id, actor_employee_id, event_type, summary, created_at)
SELECT id, created_by, 'URGENT', 'Markert som haster', updated_at
FROM tickets WHERE description LIKE '[DEMO]%' AND urgent = 1;

INSERT INTO comments(ticket_id, employee_id, text, created_at)
SELECT id, assigned_to, 'Kunden er oppdatert. Saken følges opp som avtalt.', strftime('%Y-%m-%dT%H:%M:%fZ', created_at, '+8 minutes')
FROM tickets WHERE description LIKE '[DEMO]%' AND id % 4 = 0;

INSERT OR IGNORE INTO app_settings(setting_key, setting_value)
SELECT 'current_employee', CAST(id AS TEXT) FROM employees WHERE name = 'Amalie';
