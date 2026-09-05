package no.telefonhjelp.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {
    private ApiModels() {}

    public enum TicketStatus { IN_PROGRESS, WAITING, ESCALATED, CLOSED }
    public enum DeviceType { PHONE, TABLET, SMARTWATCH, COMPUTER, OTHER }
    public enum OperatingSystem { IOS, ANDROID, OTHER }

    public record Employee(long id, String name, boolean active) {}
    public record Comment(long id, long employeeId, String employeeName, String text, Instant createdAt) {}
    public record HistoryEvent(long id, long actorEmployeeId, String actorName, String eventType, String summary, Instant createdAt) {}
    public record Ticket(
            long id, long version, String customerName, String customerPhone, String customerPhoneNormalized,
            DeviceType deviceType, String manufacturer, String deviceModel, String newDeviceModel, OperatingSystem operatingSystem,
            String category, String description, long createdById, String createdByName,
            long assignedToId, String assignedToName, TicketStatus status, boolean urgent,
            Instant createdAt, Instant updatedAt, Instant closedAt, List<Comment> comments, List<HistoryEvent> history) {}
    public record TicketDraft(
            String customerName, String customerPhone, DeviceType deviceType, String manufacturer,
            String deviceModel, String newDeviceModel, OperatingSystem operatingSystem, String category, String description, long actorId) {}
    public record TicketPatch(
            String customerName, String customerPhone, DeviceType deviceType, String manufacturer,
            String deviceModel, String newDeviceModel, OperatingSystem operatingSystem, String category, String description,
            long actorId, long version) {}
    public record Bootstrap(List<Employee> employees, Long currentEmployeeId) {}
    public record CustomerMatch(long id, String name, String phoneNormalized, long previousTickets) {}
    public record TemporaryCredential(String key, String label, String value, Instant expiresAt) {}
    public record TemporaryValue(String key, String label, String value) {}
    public record TemporaryValues(List<TemporaryValue> values) {}
    public record ReportFilter(LocalDate from, LocalDate to, Long employeeId, String category) {}
    public record ReportSummary(long created, long closed, long open, long urgent, long escalated,
                                long averageMinutes, long within30Percent, long within60Percent, long over60Percent) {}
    public record ApiError(String message) {}
}
