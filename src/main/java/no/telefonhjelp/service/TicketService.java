package no.telefonhjelp.service;

import no.telefonhjelp.domain.ApiModels.*;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

@Service
public class TicketService {
    private final JdbcTemplate jdbc;
    private final EmployeeService employees;

    public TicketService(JdbcTemplate jdbc, EmployeeService employees, Flyway flyway) { this.jdbc = jdbc; this.employees = employees; }

    public List<Ticket> list(String scope, String query, Long employeeId, String category, LocalDate from, LocalDate to) {
        var sql = new StringBuilder(BASE_SQL + " WHERE 1=1");
        var args = new ArrayList<>();
        if ("active".equals(scope)) sql.append(" AND t.status<>'CLOSED'");
        if ("closed".equals(scope)) sql.append(" AND t.status='CLOSED'");
        if (employeeId != null) { sql.append(" AND t.assigned_to=?"); args.add(employeeId); }
        if (category != null && !category.isBlank()) { sql.append(" AND t.category=?"); args.add(category); }
        var zone = ZoneId.of("Europe/Oslo");
        if (from != null) { sql.append(" AND t.created_at>=?"); args.add(from.atStartOfDay(zone).toInstant().toString()); }
        if (to != null) { sql.append(" AND t.created_at<?"); args.add(to.plusDays(1).atStartOfDay(zone).toInstant().toString()); }
        if (query != null && !query.isBlank()) {
            var needle = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            var phone = "%" + PhoneNormalizer.normalize(query) + "%";
            sql.append(" AND (CAST(t.id AS TEXT) LIKE ? OR lower(c.name) LIKE ? OR lower(c.phone_display) LIKE ? OR c.phone_normalized LIKE ? OR lower(t.device_model) LIKE ? OR lower(t.new_device_model) LIKE ? OR lower(t.description) LIKE ?)");
            args.addAll(List.of(needle, needle, needle, phone, needle, needle, needle));
        }
        sql.append(" ORDER BY t.urgent DESC, t.created_at ASC");
        return jdbc.query(sql.toString(), this::mapTicketWithoutChildren, args.toArray()).stream().map(this::withChildren).toList();
    }

    public Ticket get(long id) {
        var values = jdbc.query(BASE_SQL + " WHERE t.id=?", this::mapTicketWithoutChildren, id);
        if (values.isEmpty()) throw AppException.notFound("Fant ikke saken.");
        return withChildren(values.getFirst());
    }

    @Transactional
    public Ticket create(TicketDraft draft) {
        var actor = employees.require(draft.actorId(), true);
        validateDraft(draft.customerName(), draft.customerPhone(), draft.deviceModel(), draft.category(), draft.description());
        var now = Instant.now().toString();
        var newDeviceModel = TRANSFER_CATEGORY.equals(draft.category().trim()) ? clean(draft.newDeviceModel()) : "";
        var customerId = upsertCustomer(draft.customerName().trim(), draft.customerPhone().trim(), now);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO tickets(customer_id,device_type,manufacturer,device_model,new_device_model,operating_system,category,description,created_by,assigned_to,status,urgent,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,'IN_PROGRESS',0,?,?,0)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, customerId); statement.setString(2, draft.deviceType().name()); statement.setString(3, clean(draft.manufacturer())); statement.setString(4, draft.deviceModel().trim()); statement.setString(5, newDeviceModel); statement.setString(6, draft.operatingSystem().name()); statement.setString(7, draft.category().trim()); statement.setString(8, draft.description().trim()); statement.setLong(9, actor.id()); statement.setLong(10, actor.id()); statement.setString(11, now); statement.setString(12, now); return statement;
        }, key);
        var id = key.getKey().longValue();
        addHistory(id, actor.id(), "CREATED", "Saken ble opprettet", now);
        addHistory(id, actor.id(), "STATUS", "Status satt til Pågår", now);
        return get(id);
    }

    @Transactional
    public Ticket update(long id, TicketPatch patch) {
        var current = writable(id, patch.version());
        var actor = employees.require(patch.actorId(), true);
        var customerName = choose(patch.customerName(), current.customerName());
        var customerPhone = choose(patch.customerPhone(), current.customerPhone());
        var deviceModel = choose(patch.deviceModel(), current.deviceModel());
        var category = choose(patch.category(), current.category());
        var requestedNewDevice = patch.newDeviceModel() == null ? current.newDeviceModel() : clean(patch.newDeviceModel());
        var newDeviceModel = TRANSFER_CATEGORY.equals(category) ? requestedNewDevice : "";
        var description = choose(patch.description(), current.description());
        validateDraft(customerName, customerPhone, deviceModel, category, description);
        var now = Instant.now().toString();
        var customerId = upsertCustomer(customerName, customerPhone, now);
        var summaries = new ArrayList<String>();
        changed(summaries, "Kundenavn", current.customerName(), customerName);
        changed(summaries, "Telefonnummer", current.customerPhone(), customerPhone);
        changed(summaries, "Kategori", current.category(), category);
        changed(summaries, "Ny enhet", current.newDeviceModel(), newDeviceModel);
        changed(summaries, "Problem", current.description(), description);
        var type = patch.deviceType() == null ? current.deviceType() : patch.deviceType();
        var manufacturer = patch.manufacturer() == null ? current.manufacturer() : patch.manufacturer();
        var os = patch.operatingSystem() == null ? current.operatingSystem() : patch.operatingSystem();
        changed(summaries, "Enhet", deviceLabel(current.deviceType(), current.manufacturer(), current.deviceModel(), current.operatingSystem()), deviceLabel(type, manufacturer, deviceModel, os));
        var rows = jdbc.update("UPDATE tickets SET customer_id=?,device_type=?,manufacturer=?,device_model=?,new_device_model=?,operating_system=?,category=?,description=?,updated_at=?,version=version+1 WHERE id=? AND version=?", customerId, type.name(), clean(manufacturer), deviceModel, newDeviceModel, os.name(), category, description, now, id, patch.version());
        if (rows != 1) throw AppException.conflict("Saken er endret. Last den inn på nytt.");
        summaries.forEach(summary -> addHistory(id, actor.id(), "EDITED", summary, now));
        return get(id);
    }

    @Transactional
    public Ticket comment(long id, String text, long actorId, long version) {
        var current = writable(id, version); var actor = employees.require(actorId, true); var value = choose(text, "");
        if (value.isBlank()) throw AppException.badRequest("Kommentaren kan ikke være tom.");
        var now = Instant.now().toString();
        jdbc.update("INSERT INTO comments(ticket_id,employee_id,text,created_at) VALUES (?,?,?,?)", id, actor.id(), value, now);
        touch(id, current.version(), now); addHistory(id, actor.id(), "COMMENT", "Kommentar lagt til", now); return get(id);
    }

    @Transactional
    public Ticket assign(long id, long employeeId, long actorId, long version) {
        var current = writable(id, version); var actor = employees.require(actorId, true); var target = employees.require(employeeId, true); var now = Instant.now().toString();
        if (current.assignedToId() == employeeId) return current;
        updateVersioned("UPDATE tickets SET assigned_to=?,updated_at=?,version=version+1 WHERE id=? AND version=?", employeeId, now, id, version);
        addHistory(id, actor.id(), "ASSIGNED", "Tildelt endret: " + current.assignedToName() + " → " + target.name(), now); return get(id);
    }

    @Transactional
    public Ticket status(long id, TicketStatus status, long actorId, long version) {
        var current = get(id); var actor = employees.require(actorId, true);
        if (current.version() != version) throw AppException.conflict("Saken er endret. Last den inn på nytt.");
        if (current.status() == TicketStatus.CLOSED && status != TicketStatus.IN_PROGRESS) throw AppException.badRequest("En lukket sak kan bare åpnes igjen.");
        if (current.status() == status) return current;
        var now = Instant.now().toString(); var closed = status == TicketStatus.CLOSED ? now : null;
        updateVersioned("UPDATE tickets SET status=?,closed_at=?,updated_at=?,version=version+1 WHERE id=? AND version=?", status.name(), closed, now, id, version);
        var event = status == TicketStatus.CLOSED ? "CLOSED" : current.status() == TicketStatus.CLOSED ? "REOPENED" : "STATUS";
        addHistory(id, actor.id(), event, label(current.status()) + " → " + label(status), now); return get(id);
    }

    @Transactional
    public Ticket urgent(long id, boolean urgent, long actorId, long version) {
        var current = writable(id, version); var actor = employees.require(actorId, true);
        if (current.urgent() == urgent) return current;
        var now = Instant.now().toString(); updateVersioned("UPDATE tickets SET urgent=?,updated_at=?,version=version+1 WHERE id=? AND version=?", urgent, now, id, version);
        addHistory(id, actor.id(), "URGENT", urgent ? "Markert som haster" : "Haster-markering fjernet", now); return get(id);
    }

    private Ticket writable(long id, long version) {
        var current = get(id);
        if (current.version() != version) throw AppException.conflict("Saken er endret. Last den inn på nytt.");
        if (current.status() == TicketStatus.CLOSED) throw AppException.badRequest("Åpne saken igjen før du gjør endringer.");
        return current;
    }

    private long upsertCustomer(String name, String displayPhone, String now) {
        var normalized = PhoneNormalizer.normalize(displayPhone);
        var ids = jdbc.query("SELECT id FROM customers WHERE phone_normalized=?", (rs, n) -> rs.getLong(1), normalized);
        if (!ids.isEmpty()) { jdbc.update("UPDATE customers SET name=?,phone_display=?,updated_at=? WHERE id=?", name, displayPhone, now, ids.getFirst()); return ids.getFirst(); }
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement("INSERT INTO customers(phone_normalized,phone_display,name,created_at,updated_at) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); statement.setString(1, normalized); statement.setString(2, displayPhone); statement.setString(3, name); statement.setString(4, now); statement.setString(5, now); return statement; }, key);
        return key.getKey().longValue();
    }

    private void touch(long id, long version, String now) { updateVersioned("UPDATE tickets SET updated_at=?,version=version+1 WHERE id=? AND version=?", now, id, version); }
    private void updateVersioned(String sql, Object... args) { if (jdbc.update(sql, args) != 1) throw AppException.conflict("Saken er endret. Last den inn på nytt."); }
    private void addHistory(long ticketId, long actorId, String type, String summary, String now) { jdbc.update("INSERT INTO ticket_history(ticket_id,actor_employee_id,event_type,summary,created_at) VALUES (?,?,?,?,?)", ticketId, actorId, type, summary, now); }
    private static void validateDraft(String name, String phone, String device, String category, String description) { if (Stream.of(name, phone, device, category, description).anyMatch(value -> value == null || value.isBlank())) throw AppException.badRequest("Fyll ut alle obligatoriske felt."); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String choose(String value, String fallback) { return value == null ? fallback : value.trim(); }
    private static String deviceLabel(DeviceType type, String manufacturer, String model, OperatingSystem os) { return Stream.of(type.name(), clean(manufacturer), model, os.name()).filter(value -> !value.isBlank()).reduce((left, right) -> left + " / " + right).orElse(""); }
    private static void changed(List<String> values, String label, String oldValue, String newValue) { if (!Objects.equals(oldValue, newValue)) values.add(label + " endret: " + oldValue + " → " + newValue); }
    private static String label(TicketStatus status) { return switch (status) { case IN_PROGRESS -> "Pågår"; case WAITING -> "Venter"; case ESCALATED -> "Eskalert"; case CLOSED -> "Lukket"; }; }

    private Ticket withChildren(Ticket ticket) {
        var comments = jdbc.query("SELECT c.id,c.employee_id,e.name employee_name,c.text,c.created_at FROM comments c JOIN employees e ON e.id=c.employee_id WHERE c.ticket_id=? ORDER BY c.created_at", (rs, n) -> new Comment(rs.getLong("id"), rs.getLong("employee_id"), rs.getString("employee_name"), rs.getString("text"), Instant.parse(rs.getString("created_at"))), ticket.id());
        var history = jdbc.query("SELECT h.id,h.actor_employee_id,e.name actor_name,h.event_type,h.summary,h.created_at FROM ticket_history h JOIN employees e ON e.id=h.actor_employee_id WHERE h.ticket_id=? ORDER BY h.created_at,h.id", (rs, n) -> new HistoryEvent(rs.getLong("id"), rs.getLong("actor_employee_id"), rs.getString("actor_name"), rs.getString("event_type"), rs.getString("summary"), Instant.parse(rs.getString("created_at"))), ticket.id());
        return new Ticket(ticket.id(), ticket.version(), ticket.customerName(), ticket.customerPhone(), ticket.customerPhoneNormalized(), ticket.deviceType(), ticket.manufacturer(), ticket.deviceModel(), ticket.newDeviceModel(), ticket.operatingSystem(), ticket.category(), ticket.description(), ticket.createdById(), ticket.createdByName(), ticket.assignedToId(), ticket.assignedToName(), ticket.status(), ticket.urgent(), ticket.createdAt(), ticket.updatedAt(), ticket.closedAt(), comments, history);
    }

    private Ticket mapTicketWithoutChildren(ResultSet rs, int row) throws SQLException {
        var closed = rs.getString("closed_at");
        return new Ticket(rs.getLong("id"), rs.getLong("version"), rs.getString("customer_name"), rs.getString("phone_display"), rs.getString("phone_normalized"), DeviceType.valueOf(rs.getString("device_type")), rs.getString("manufacturer"), rs.getString("device_model"), rs.getString("new_device_model"), OperatingSystem.valueOf(rs.getString("operating_system")), rs.getString("category"), rs.getString("description"), rs.getLong("created_by"), rs.getString("created_by_name"), rs.getLong("assigned_to"), rs.getString("assigned_to_name"), TicketStatus.valueOf(rs.getString("status")), rs.getBoolean("urgent"), Instant.parse(rs.getString("created_at")), Instant.parse(rs.getString("updated_at")), closed == null ? null : Instant.parse(closed), List.of(), List.of());
    }

    private static final String BASE_SQL = "SELECT t.*,c.name customer_name,c.phone_display,c.phone_normalized,creator.name created_by_name,assigned.name assigned_to_name FROM tickets t JOIN customers c ON c.id=t.customer_id JOIN employees creator ON creator.id=t.created_by JOIN employees assigned ON assigned.id=t.assigned_to";
    private static final String TRANSFER_CATEGORY = "Dataoverføring / sikkerhetskopi / oppsett";
}
