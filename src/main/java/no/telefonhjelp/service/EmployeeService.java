package no.telefonhjelp.service;

import no.telefonhjelp.domain.ApiModels.Employee;
import org.flywaydb.core.Flyway;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.time.Instant;
import java.util.List;

@Service
public class EmployeeService {
    private final JdbcTemplate jdbc;

    public EmployeeService(JdbcTemplate jdbc, Flyway flyway) { this.jdbc = jdbc; }

    public List<Employee> list() {
        return jdbc.query("SELECT id, name, active FROM employees ORDER BY active DESC, name COLLATE NOCASE", (rs, n) -> new Employee(rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")));
    }

    public Employee require(long id, boolean mustBeActive) {
        var values = jdbc.query("SELECT id, name, active FROM employees WHERE id=?", (rs, n) -> new Employee(rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")), id);
        if (values.isEmpty()) throw AppException.notFound("Fant ikke den ansatte.");
        if (mustBeActive && !values.getFirst().active()) throw AppException.badRequest("Den ansatte er deaktivert.");
        return values.getFirst();
    }

    @Transactional
    public Employee create(String name) {
        var clean = required(name, "Navn er påkrevd.");
        var key = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("INSERT INTO employees(name, active, created_at) VALUES (?,1,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, clean); statement.setString(2, Instant.now().toString()); return statement;
            }, key);
        } catch (Exception exception) { throw new AppException(HttpStatus.CONFLICT, "En ansatt med dette navnet finnes allerede."); }
        return require(key.getKey().longValue(), false);
    }

    @Transactional
    public Employee update(long id, String name, Boolean active) {
        var current = require(id, false);
        var nextName = name == null ? current.name() : required(name, "Navn er påkrevd.");
        var nextActive = active == null ? current.active() : active;
        if (!nextActive && jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE active=1", Long.class) <= 1) throw AppException.badRequest("Minst én ansatt må være aktiv.");
        jdbc.update("UPDATE employees SET name=?, active=? WHERE id=?", nextName, nextActive, id);
        return require(id, false);
    }

    public Long currentEmployeeId() {
        var values = jdbc.query("SELECT CAST(setting_value AS INTEGER) id FROM app_settings WHERE setting_key='current_employee'", (rs, n) -> rs.getLong("id"));
        if (!values.isEmpty()) {
            try { if (require(values.getFirst(), true) != null) return values.getFirst(); } catch (AppException ignored) {}
        }
        return list().stream().filter(Employee::active).map(Employee::id).findFirst().orElse(null);
    }

    public void setCurrentEmployee(long id) {
        require(id, true);
        jdbc.update("INSERT INTO app_settings(setting_key,setting_value) VALUES ('current_employee',?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value", Long.toString(id));
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw AppException.badRequest(message);
        return value.trim();
    }
}

