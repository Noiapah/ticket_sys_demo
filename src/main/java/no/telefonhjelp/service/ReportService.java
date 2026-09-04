package no.telefonhjelp.service;

import no.telefonhjelp.domain.ApiModels.ReportFilter;
import no.telefonhjelp.domain.ApiModels.ReportSummary;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

@Service
public class ReportService {
    private final JdbcTemplate jdbc;
    public ReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ReportSummary summary(ReportFilter filter) {
        var range = range(filter); var args = new ArrayList<Object>(); args.add(range.from()); args.add(range.to());
        var categorySql = filter.category() != null && !filter.category().isBlank() ? " AND t.category=?" : "";
        if (!categorySql.isEmpty()) args.add(filter.category());
        var employeeSql = filter.employeeId() != null ? " AND EXISTS(SELECT 1 FROM ticket_history eh WHERE eh.ticket_id=t.id AND eh.actor_employee_id=? AND eh.created_at>=? AND eh.created_at<?)" : "";
        if (filter.employeeId() != null) { args.add(filter.employeeId()); args.add(range.from()); args.add(range.to()); }
        var tickets = jdbc.query("SELECT t.status,(t.urgent=1 OR EXISTS(SELECT 1 FROM ticket_history hu WHERE hu.ticket_id=t.id AND hu.event_type='URGENT' AND hu.summary='Markert som haster')) urgent,t.created_at,t.closed_at, EXISTS(SELECT 1 FROM ticket_history h WHERE h.ticket_id=t.id AND h.summary LIKE '%Eskalert%') escalated FROM tickets t WHERE t.created_at>=? AND t.created_at<?" + categorySql + employeeSql, (rs, n) -> new ReportRow(rs.getString("status"), rs.getBoolean("urgent"), Instant.parse(rs.getString("created_at")), rs.getString("closed_at") == null ? null : Instant.parse(rs.getString("closed_at")), rs.getBoolean("escalated")), args.toArray());
        var closedArgs = new ArrayList<Object>(); closedArgs.add(range.from()); closedArgs.add(range.to());
        var closedCategory = filter.category() != null && !filter.category().isBlank() ? " AND t.category=?" : ""; if (!closedCategory.isEmpty()) closedArgs.add(filter.category());
        var closedEmployee = filter.employeeId() != null ? " AND EXISTS(SELECT 1 FROM ticket_history hc WHERE hc.ticket_id=t.id AND hc.actor_employee_id=? AND hc.created_at>=? AND hc.created_at<?)" : ""; if (filter.employeeId()!=null) { closedArgs.add(filter.employeeId()); closedArgs.add(range.from()); closedArgs.add(range.to()); }
        var durations = jdbc.query("SELECT t.created_at,t.closed_at FROM tickets t WHERE t.closed_at>=? AND t.closed_at<?"+closedCategory+closedEmployee, (rs,n)->ChronoUnit.MINUTES.between(Instant.parse(rs.getString(1)),Instant.parse(rs.getString(2))), closedArgs.toArray());
        return new ReportSummary(tickets.size(), durations.size(), tickets.stream().filter(row -> !"CLOSED".equals(row.status())).count(), tickets.stream().filter(ReportRow::urgent).count(), tickets.stream().filter(ReportRow::escalated).count(), durations.isEmpty() ? 0 : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0)), percent(durations, 0, 30), percent(durations, 0, 60), percent(durations, 61, Long.MAX_VALUE));
    }

    public byte[] workbook(ReportFilter filter) throws Exception {
        var summary = summary(filter);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var header = workbook.createCellStyle(); header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var overview = workbook.createSheet("Sammendrag");
            row(overview, 0, header, "Måling", "Verdi");
            row(overview, 1, null, "Opprettet", summary.created()); row(overview, 2, null, "Lukket", summary.closed()); row(overview, 3, null, "Åpne", summary.open()); row(overview, 4, null, "Gjennomsnitt minutter", summary.averageMinutes()); row(overview, 5, null, "Innen 30 minutter (%)", summary.within30Percent()); row(overview, 6, null, "Innen 60 minutter (%)", summary.within60Percent()); row(overview, 7, null, "Over 60 minutter (%)", summary.over60Percent()); row(overview, 8, null, "Haster", summary.urgent()); row(overview, 9, null, "Eskalert", summary.escalated());
            var tickets = workbook.createSheet("Saker"); row(tickets, 0, header, "Saksnummer", "Opprettet", "Lukket", "Status", "Kategori", "Produsent", "Modell", "Opprettet av", "Tildelt", "Haster");
            var range = range(filter); var ticketArgs = new ArrayList<Object>(); ticketArgs.add(range.from()); ticketArgs.add(range.to());
            var ticketFilter = new StringBuilder();
            if (filter.category() != null && !filter.category().isBlank()) { ticketFilter.append(" AND t.category=?"); ticketArgs.add(filter.category()); }
            if (filter.employeeId() != null) { ticketFilter.append(" AND EXISTS(SELECT 1 FROM ticket_history fh WHERE fh.ticket_id=t.id AND fh.actor_employee_id=? AND fh.created_at>=? AND fh.created_at<?)"); ticketArgs.add(filter.employeeId()); ticketArgs.add(range.from()); ticketArgs.add(range.to()); }
            var values = jdbc.query("SELECT t.id,t.created_at,t.closed_at,t.status,t.category,t.manufacturer,t.device_model,creator.name creator,assigned.name assigned,t.urgent FROM tickets t JOIN employees creator ON creator.id=t.created_by JOIN employees assigned ON assigned.id=t.assigned_to WHERE t.created_at>=? AND t.created_at<?" + ticketFilter + " ORDER BY t.created_at", (rs, n) -> new Object[]{rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getBoolean(10) ? "Ja" : "Nei"}, ticketArgs.toArray());
            for (var i = 0; i < values.size(); i++) row(tickets, i + 1, null, values.get(i));
            var employees = workbook.createSheet("Ansatte"); row(employees, 0, header, "Ansatt", "Saker med handlinger");
            var employeeArgs = new ArrayList<Object>(); employeeArgs.add(range.from()); employeeArgs.add(range.to());
            var employeeFilter = new StringBuilder();
            if (filter.category() != null && !filter.category().isBlank()) { employeeFilter.append(" AND et.category=?"); employeeArgs.add(filter.category()); }
            if (filter.employeeId() != null) { employeeFilter.append(" AND e.id=?"); employeeArgs.add(filter.employeeId()); }
            var employeeRows = jdbc.query("SELECT e.name,COUNT(DISTINCT h.ticket_id) handled FROM employees e LEFT JOIN ticket_history h ON h.actor_employee_id=e.id AND h.created_at>=? AND h.created_at<? LEFT JOIN tickets et ON et.id=h.ticket_id WHERE 1=1" + employeeFilter + " GROUP BY e.id,e.name ORDER BY e.name", (rs, n) -> new Object[]{rs.getString(1),rs.getLong(2)}, employeeArgs.toArray());
            for (var i = 0; i < employeeRows.size(); i++) row(employees, i + 1, null, employeeRows.get(i));
            var categories = workbook.createSheet("Kategorier"); row(categories, 0, header, "Kategori", "Antall"); groupedSheet(categories, "t.category", filter, range);
            var devices = workbook.createSheet("Enheter"); row(devices, 0, header, "Modell", "Antall"); groupedSheet(devices, "t.device_model", filter, range);
            for (var sheet : workbook) { sheet.createFreezePane(0, 1); for (var column = 0; column < Math.min(10, sheet.getRow(0).getLastCellNum()); column++) sheet.autoSizeColumn(column); }
            workbook.write(output); return output.toByteArray();
        }
    }

    private void groupedSheet(org.apache.poi.ss.usermodel.Sheet sheet, String column, ReportFilter filter, Range range) {
        var args = new ArrayList<Object>(); args.add(range.from()); args.add(range.to()); var where = new StringBuilder();
        if (filter.category() != null && !filter.category().isBlank()) { where.append(" AND t.category=?"); args.add(filter.category()); }
        if (filter.employeeId() != null) { where.append(" AND EXISTS(SELECT 1 FROM ticket_history gh WHERE gh.ticket_id=t.id AND gh.actor_employee_id=? AND gh.created_at>=? AND gh.created_at<?)"); args.add(filter.employeeId()); args.add(range.from()); args.add(range.to()); }
        var rows = jdbc.query("SELECT " + column + ",COUNT(*) FROM tickets t WHERE t.created_at>=? AND t.created_at<?" + where + " GROUP BY " + column + " ORDER BY COUNT(*) DESC", (rs, n) -> new Object[]{rs.getString(1),rs.getLong(2)}, args.toArray());
        for (var i=0;i<rows.size();i++) row(sheet,i+1,null,rows.get(i));
    }
    private static void row(org.apache.poi.ss.usermodel.Sheet sheet, int index, CellStyle style, Object... values) { var row=sheet.createRow(index); for(var i=0;i<values.length;i++){var cell=row.createCell(i); var value=values[i]; if(value instanceof Number number) cell.setCellValue(number.doubleValue()); else cell.setCellValue(value==null?"":value.toString()); if(style!=null) cell.setCellStyle(style);} }
    private static long percent(java.util.List<Long> durations, long min, long max) { return durations.isEmpty()?0:Math.round(durations.stream().filter(value->value>=min&&value<=max).count()*100.0/durations.size()); }
    private static Range range(ReportFilter filter) { var zone=ZoneId.of("Europe/Oslo"); return new Range(filter.from().atStartOfDay(zone).toInstant().toString(),filter.to().plusDays(1).atStartOfDay(zone).toInstant().toString()); }
    private record Range(String from,String to) {}
    private record ReportRow(String status,boolean urgent,Instant createdAt,Instant closedAt,boolean escalated) {}
}
