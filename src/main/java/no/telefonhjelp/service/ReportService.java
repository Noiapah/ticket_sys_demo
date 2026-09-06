package no.telefonhjelp.service;

import no.telefonhjelp.domain.ApiModels.ReportFilter;
import no.telefonhjelp.domain.ApiModels.ReportSummary;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.MarkerStyle;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class ReportService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Europe/Oslo");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final Locale NORWEGIAN = Locale.forLanguageTag("nb-NO");
    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ReportSummary summary(ReportFilter filter) { return summarize(loadData(filter)); }

    public byte[] workbook(ReportFilter filter) throws Exception {
        var data = loadData(filter);
        var summary = summarize(data);
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var styles = styles(workbook);
            overviewSheet(workbook, filter, data, summary, styles);
            ticketSheet(workbook, filter, styles);
            employeeSheet(workbook, filter, styles);
            groupedSheet(workbook, "Kategorier", "Kategori", "t.category", filter, styles);
            groupedSheet(workbook, "Enheter", "Modell", "t.device_model", filter, styles);
            groupedSheet(workbook, "Produsenter", "Produsent", "t.manufacturer", filter, styles);
            trendSheet(workbook, filter, data, styles);
            workbook.setActiveSheet(0);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private ReportData loadData(ReportFilter filter) {
        var range = range(filter);
        var ticketArgs = new ArrayList<Object>();
        ticketArgs.add(range.from()); ticketArgs.add(range.to());
        var ticketWhere = filters(filter, range, ticketArgs, "eh");
        var tickets = jdbc.query("SELECT t.status,(t.urgent=1 OR EXISTS(SELECT 1 FROM ticket_history hu WHERE hu.ticket_id=t.id AND hu.event_type='URGENT' AND hu.summary='Markert som haster')) urgent,t.created_at,EXISTS(SELECT 1 FROM ticket_history h WHERE h.ticket_id=t.id AND h.summary LIKE '%Eskalert%') escalated FROM tickets t WHERE t.created_at>=? AND t.created_at<?" + ticketWhere,
                (rs, n) -> new ReportRow(rs.getString("status"), rs.getBoolean("urgent"), Instant.parse(rs.getString("created_at")), rs.getBoolean("escalated")), ticketArgs.toArray());

        var closedArgs = new ArrayList<Object>();
        closedArgs.add(range.from()); closedArgs.add(range.to());
        var closedWhere = filters(filter, range, closedArgs, "hc");
        var closed = jdbc.query("SELECT t.created_at,t.closed_at FROM tickets t WHERE t.closed_at>=? AND t.closed_at<?" + closedWhere,
                (rs, n) -> {
                    var createdAt = Instant.parse(rs.getString("created_at"));
                    var closedAt = Instant.parse(rs.getString("closed_at"));
                    return new ClosedRow(ChronoUnit.MINUTES.between(createdAt, closedAt), closedAt);
                }, closedArgs.toArray());
        return new ReportData(tickets, closed);
    }

    private static String filters(ReportFilter filter, Range range, List<Object> args, String historyAlias) {
        var sql = new StringBuilder();
        if (filter.category() != null && !filter.category().isBlank()) {
            sql.append(" AND t.category=?");
            args.add(filter.category());
        }
        if (filter.employeeId() != null) {
            sql.append(" AND EXISTS(SELECT 1 FROM ticket_history ").append(historyAlias).append(" WHERE ").append(historyAlias).append(".ticket_id=t.id AND ").append(historyAlias).append(".actor_employee_id=? AND ").append(historyAlias).append(".created_at>=? AND ").append(historyAlias).append(".created_at<?)");
            args.add(filter.employeeId()); args.add(range.from()); args.add(range.to());
        }
        return sql.toString();
    }

    private static ReportSummary summarize(ReportData data) {
        var durations = data.closed().stream().map(ClosedRow::minutes).toList();
        return new ReportSummary(
                data.tickets().size(), durations.size(),
                data.tickets().stream().filter(row -> !"CLOSED".equals(row.status())).count(),
                data.tickets().stream().filter(ReportRow::urgent).count(),
                data.tickets().stream().filter(ReportRow::escalated).count(),
                durations.isEmpty() ? 0 : Math.round(durations.stream().mapToLong(Long::longValue).average().orElse(0)),
                percent(durations, 0, 30), percent(durations, 0, 60), percent(durations, 61, Long.MAX_VALUE));
    }

    private void overviewSheet(XSSFWorkbook workbook, ReportFilter filter, ReportData data, ReportSummary summary, Styles styles) {
        var sheet = workbook.createSheet("Sammendrag");
        sheet.setDisplayGridlines(false);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 9));
        row(sheet, 0, styles.title(), "Telefonhjelp – rapportoversikt");
        sheet.getRow(0).setHeightInPoints(34);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 9));
        row(sheet, 1, styles.subtitle(), filterDescription(filter));

        metric(sheet, 3, 4, 0, "Opprettet", summary.created(), styles);
        metric(sheet, 3, 4, 2, "Lukket", summary.closed(), styles);
        metric(sheet, 3, 4, 4, "Åpne", summary.open(), styles);
        metric(sheet, 3, 4, 6, "Haster", summary.urgent(), styles);
        metric(sheet, 3, 4, 8, "Eskalert", summary.escalated(), styles);
        metric(sheet, 6, 7, 0, "Gjennomsnitt", summary.averageMinutes() + " min", styles);
        metric(sheet, 6, 7, 2, "Innen 30 min", summary.within30Percent() + " %", styles);
        metric(sheet, 6, 7, 4, "Innen 60 min", summary.within60Percent() + " %", styles);
        metric(sheet, 6, 7, 6, "Over 60 min", summary.over60Percent() + " %", styles);
        var days = Math.max(1, ChronoUnit.DAYS.between(filter.from(), filter.to()) + 1);
        metric(sheet, 6, 7, 8, "Snitt per dag", String.format(NORWEGIAN, "%.1f", summary.created() / (double) days), styles);

        sheet.addMergedRegion(new CellRangeAddress(9, 9, 0, 9));
        row(sheet, 9, styles.section(), "Spredning i behandlingstid");
        var durations = data.closed().stream().map(ClosedRow::minutes).sorted().toList();
        spreadMetric(sheet, 10, 11, 0, "Raskest", percentile(durations, 0), styles);
        spreadMetric(sheet, 10, 11, 2, "Median", percentile(durations, 50), styles);
        spreadMetric(sheet, 10, 11, 4, "Gjennomsnitt", summary.averageMinutes(), styles);
        spreadMetric(sheet, 10, 11, 6, "90-persentil", percentile(durations, 90), styles);
        spreadMetric(sheet, 10, 11, 8, "Lengst", percentile(durations, 100), styles);

        row(sheet, 14, styles.header(), "Behandlingstid", "Antall", "Andel");
        var totalClosed = Math.max(1, durations.size());
        var within30 = count(durations, 0, 30);
        var between31And60 = count(durations, 31, 60);
        var over60 = count(durations, 61, Long.MAX_VALUE);
        distributionRow(sheet, 15, "0–30 min", within30, within30 / (double) totalClosed, styles);
        distributionRow(sheet, 16, "31–60 min", between31And60, between31And60 / (double) totalClosed, styles);
        distributionRow(sheet, 17, "Over 60 min", over60, over60 / (double) totalClosed, styles);

        rowAt(sheet, 14, 4, styles.header(), "Status", "Antall");
        var statuses = List.of("IN_PROGRESS", "WAITING", "ESCALATED", "CLOSED");
        for (var i = 0; i < statuses.size(); i++) {
            var status = statuses.get(i);
            rowAt(sheet, 15 + i, 4, i % 2 == 0 ? styles.body() : styles.stripe(), statusLabel(status), data.tickets().stream().filter(ticket -> status.equals(ticket.status())).count());
        }

        rowAt(sheet, 14, 8, styles.header(), "Signal", "Antall");
        rowAt(sheet, 15, 8, styles.body(), "Haster", summary.urgent());
        rowAt(sheet, 16, 8, styles.stripe(), "Eskalert", summary.escalated());

        addColumnChart(sheet, "Fordeling av behandlingstid", 0, 1, 15, 17, 0, 20, 5, 36, "Antall saker");
        addPieChart(sheet, "Status på opprettede saker", 4, 5, 15, 18, 5, 20, 10, 36);
        for (var column = 0; column < 10; column++) sheet.setColumnWidth(column, column % 2 == 0 ? 18 * 256 : 4 * 256);
        sheet.setColumnWidth(1, 13 * 256); sheet.setColumnWidth(2, 13 * 256); sheet.setColumnWidth(5, 13 * 256); sheet.setColumnWidth(9, 13 * 256);
        sheet.createFreezePane(0, 2);
        printSetup(sheet);
    }

    private void ticketSheet(XSSFWorkbook workbook, ReportFilter filter, Styles styles) {
        var sheet = workbook.createSheet("Saker");
        row(sheet, 0, styles.header(), "Saksnummer", "Opprettet", "Lukket", "Behandlingstid (min)", "Status", "Kategori", "Enhetstype", "Produsent", "Modell", "Operativsystem", "Opprettet av", "Tildelt", "Haster");
        var range = range(filter);
        var args = new ArrayList<Object>(); args.add(range.from()); args.add(range.to());
        var where = filters(filter, range, args, "fh");
        var values = jdbc.query("SELECT t.id,t.created_at,t.closed_at,t.status,t.category,t.device_type,t.manufacturer,t.device_model,t.operating_system,creator.name creator,assigned.name assigned,t.urgent FROM tickets t JOIN employees creator ON creator.id=t.created_by JOIN employees assigned ON assigned.id=t.assigned_to WHERE t.created_at>=? AND t.created_at<?" + where + " ORDER BY t.created_at",
                (rs, n) -> {
                    var createdAt = Instant.parse(rs.getString("created_at"));
                    var closedValue = rs.getString("closed_at");
                    var closedAt = closedValue == null ? null : Instant.parse(closedValue);
                    return new Object[]{rs.getLong("id"), displayTime(createdAt), closedAt == null ? "" : displayTime(closedAt), closedAt == null ? null : ChronoUnit.MINUTES.between(createdAt, closedAt), statusLabel(rs.getString("status")), rs.getString("category"), deviceLabel(rs.getString("device_type")), rs.getString("manufacturer"), rs.getString("device_model"), operatingSystemLabel(rs.getString("operating_system")), rs.getString("creator"), rs.getString("assigned"), rs.getBoolean("urgent") ? "Ja" : "Nei"};
                }, args.toArray());
        for (var i = 0; i < values.size(); i++) row(sheet, i + 1, i % 2 == 0 ? styles.body() : styles.stripe(), values.get(i));
        finishTable(sheet, values.size(), 13);
    }

    private void employeeSheet(XSSFWorkbook workbook, ReportFilter filter, Styles styles) {
        var sheet = workbook.createSheet("Ansatte");
        row(sheet, 0, styles.header(), "Ansatt", "Saker med handlinger");
        var range = range(filter);
        var args = new ArrayList<Object>(); args.add(range.from()); args.add(range.to());
        var where = new StringBuilder();
        if (filter.category() != null && !filter.category().isBlank()) { where.append(" AND et.category=?"); args.add(filter.category()); }
        if (filter.employeeId() != null) { where.append(" AND e.id=?"); args.add(filter.employeeId()); }
        var values = jdbc.query("SELECT e.name,COUNT(DISTINCT h.ticket_id) handled FROM employees e LEFT JOIN ticket_history h ON h.actor_employee_id=e.id AND h.created_at>=? AND h.created_at<? LEFT JOIN tickets et ON et.id=h.ticket_id WHERE 1=1" + where + " GROUP BY e.id,e.name ORDER BY handled DESC,e.name",
                (rs, n) -> new Object[]{rs.getString("name"), rs.getLong("handled")}, args.toArray());
        for (var i = 0; i < values.size(); i++) row(sheet, i + 1, i % 2 == 0 ? styles.body() : styles.stripe(), values.get(i));
        finishTable(sheet, values.size(), 2);
        addColumnChart(sheet, "Saker per ansatt", 0, 1, 1, values.size(), 3, 1, 12, 20, "Saker med handlinger");
    }

    private void groupedSheet(XSSFWorkbook workbook, String sheetName, String heading, String column, ReportFilter filter, Styles styles) {
        var sheet = workbook.createSheet(sheetName);
        row(sheet, 0, styles.header(), heading, "Antall");
        var range = range(filter);
        var args = new ArrayList<Object>(); args.add(range.from()); args.add(range.to());
        var where = filters(filter, range, args, "gh");
        var values = jdbc.query("SELECT " + column + " label,COUNT(*) amount FROM tickets t WHERE t.created_at>=? AND t.created_at<?" + where + " GROUP BY " + column + " ORDER BY amount DESC",
                (rs, n) -> new Object[]{rs.getString("label"), rs.getLong("amount")}, args.toArray());
        for (var i = 0; i < values.size(); i++) row(sheet, i + 1, i % 2 == 0 ? styles.body() : styles.stripe(), values.get(i));
        finishTable(sheet, values.size(), 2);
        addColumnChart(sheet, "Fordeling per " + heading.toLowerCase(NORWEGIAN), 0, 1, 1, values.size(), 3, 1, 12, 20, "Antall saker");
    }

    private void trendSheet(XSSFWorkbook workbook, ReportFilter filter, ReportData data, Styles styles) {
        var sheet = workbook.createSheet("Trender");
        row(sheet, 0, styles.header(), "Periode", "Opprettet", "Lukket");
        var points = trendPoints(filter, data);
        for (var i = 0; i < points.size(); i++) {
            var point = points.get(i);
            row(sheet, i + 1, i % 2 == 0 ? styles.body() : styles.stripe(), point.label(), point.created(), point.closed());
        }
        finishTable(sheet, points.size(), 3);
        addLineChart(sheet, "Utvikling over tid", 0, 1, 2, 1, points.size(), 4, 1, 14, 22);
    }

    private static List<TrendPoint> trendPoints(ReportFilter filter, ReportData data) {
        var days = ChronoUnit.DAYS.between(filter.from(), filter.to()) + 1;
        var daily = days <= 90;
        var values = new LinkedHashMap<String, long[]>();
        if (daily) {
            for (var date = filter.from(); !date.isAfter(filter.to()); date = date.plusDays(1)) values.put(date.toString(), new long[2]);
        } else {
            for (var month = YearMonth.from(filter.from()); !month.isAfter(YearMonth.from(filter.to())); month = month.plusMonths(1)) values.put(month.toString(), new long[2]);
        }
        data.tickets().forEach(ticket -> increment(values, bucket(ticket.createdAt(), daily), 0));
        data.closed().forEach(closed -> increment(values, bucket(closed.closedAt(), daily), 1));
        var dateLabel = DateTimeFormatter.ofPattern("dd. MMM", NORWEGIAN);
        var monthLabel = DateTimeFormatter.ofPattern("MMM yyyy", NORWEGIAN);
        return values.entrySet().stream().map(entry -> new TrendPoint(
                daily ? LocalDate.parse(entry.getKey()).format(dateLabel) : YearMonth.parse(entry.getKey()).format(monthLabel),
                entry.getValue()[0], entry.getValue()[1])).toList();
    }

    private static String bucket(Instant instant, boolean daily) {
        var date = instant.atZone(REPORT_ZONE).toLocalDate();
        return daily ? date.toString() : YearMonth.from(date).toString();
    }

    private static void increment(LinkedHashMap<String, long[]> values, String key, int index) {
        var counts = values.get(key);
        if (counts != null) counts[index]++;
    }

    private String filterDescription(ReportFilter filter) {
        var employee = filter.employeeId() == null ? "Alle" : jdbc.query("SELECT name FROM employees WHERE id=?", rs -> rs.next() ? rs.getString(1) : "Ukjent", filter.employeeId());
        var category = filter.category() == null || filter.category().isBlank() ? "Alle" : filter.category();
        return "Periode: " + filter.from().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + " – " + filter.to().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "   |   Ansatt: " + employee + "   |   Kategori: " + category;
    }

    private static void metric(XSSFSheet sheet, int labelRow, int valueRow, int column, String label, Object value, Styles styles) {
        cell(sheet, labelRow, column, styles.metricLabel(), label);
        cell(sheet, valueRow, column, styles.metricValue(), value);
        sheet.addMergedRegion(new CellRangeAddress(labelRow, labelRow, column, column + 1));
        sheet.addMergedRegion(new CellRangeAddress(valueRow, valueRow, column, column + 1));
    }

    private static void spreadMetric(XSSFSheet sheet, int labelRow, int valueRow, int column, String label, long minutes, Styles styles) {
        cell(sheet, labelRow, column, styles.metricLabel(), label);
        cell(sheet, valueRow, column, styles.metricValue(), minutes + " min");
        sheet.addMergedRegion(new CellRangeAddress(labelRow, labelRow, column, column + 1));
        sheet.addMergedRegion(new CellRangeAddress(valueRow, valueRow, column, column + 1));
    }

    private static void distributionRow(XSSFSheet sheet, int index, String label, long amount, double share, Styles styles) {
        row(sheet, index, index % 2 == 0 ? styles.stripe() : styles.body(), label, amount, share);
        sheet.getRow(index).getCell(2).setCellStyle(styles.percentage());
    }

    private static Styles styles(XSSFWorkbook workbook) {
        var title = workbook.createCellStyle();
        title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        title.setAlignment(HorizontalAlignment.LEFT); title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setFont(font(workbook, IndexedColors.WHITE, 20, true));

        var subtitle = workbook.createCellStyle();
        subtitle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex()); subtitle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        subtitle.setFont(font(workbook, IndexedColors.DARK_BLUE, 11, true)); subtitle.setVerticalAlignment(VerticalAlignment.CENTER);

        var section = workbook.createCellStyle();
        section.setFillForegroundColor(IndexedColors.TEAL.getIndex()); section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        section.setFont(font(workbook, IndexedColors.WHITE, 12, true)); section.setVerticalAlignment(VerticalAlignment.CENTER);

        var header = workbook.createCellStyle();
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setFont(font(workbook, IndexedColors.WHITE, 11, true)); header.setVerticalAlignment(VerticalAlignment.CENTER);
        borders(header);

        var body = workbook.createCellStyle(); body.setVerticalAlignment(VerticalAlignment.CENTER); borders(body);
        var stripe = workbook.createCellStyle();
        stripe.cloneStyleFrom(body); stripe.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex()); stripe.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        var metricLabel = workbook.createCellStyle();
        metricLabel.setFillForegroundColor(IndexedColors.TEAL.getIndex()); metricLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        metricLabel.setAlignment(HorizontalAlignment.CENTER); metricLabel.setFont(font(workbook, IndexedColors.WHITE, 10, true)); borders(metricLabel);
        var metricValue = workbook.createCellStyle();
        metricValue.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex()); metricValue.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        metricValue.setAlignment(HorizontalAlignment.CENTER); metricValue.setVerticalAlignment(VerticalAlignment.CENTER);
        metricValue.setFont(font(workbook, IndexedColors.DARK_BLUE, 17, true)); borders(metricValue);

        var percentage = workbook.createCellStyle(); percentage.cloneStyleFrom(body); percentage.setDataFormat(workbook.createDataFormat().getFormat("0%"));
        return new Styles(title, subtitle, section, header, body, stripe, metricLabel, metricValue, percentage);
    }

    private static XSSFFont font(XSSFWorkbook workbook, IndexedColors color, int points, boolean bold) {
        var font = workbook.createFont(); font.setColor(color.getIndex()); font.setFontHeightInPoints((short) points); font.setBold(bold); return font;
    }

    private static void borders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN); style.setBorderRight(BorderStyle.THIN); style.setBorderBottom(BorderStyle.THIN); style.setBorderLeft(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    private static void finishTable(XSSFSheet sheet, int dataRows, int columns) {
        sheet.createFreezePane(0, 1);
        if (dataRows > 0) sheet.setAutoFilter(new CellRangeAddress(0, dataRows, 0, columns - 1));
        for (var column = 0; column < columns; column++) {
            sheet.autoSizeColumn(column);
            sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 512, 42 * 256));
        }
        printSetup(sheet);
    }

    private static void printSetup(XSSFSheet sheet) {
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.getFooter().setCenter("Telefonhjelp – rapport");
        sheet.getFooter().setRight("Side &P av &N");
    }

    private static void addColumnChart(XSSFSheet sheet, String title, int categoryColumn, int valueColumn, int firstRow, int lastRow, int left, int top, int right, int bottom, String seriesTitle) {
        if (lastRow < firstRow) return;
        var chart = chart(sheet, title, left, top, right, bottom);
        var categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        var valueAxis = chart.createValueAxis(AxisPosition.LEFT); valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        var categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(firstRow, lastRow, categoryColumn, categoryColumn));
        var values = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(firstRow, lastRow, valueColumn, valueColumn));
        var data = (XDDFBarChartData) chart.createData(ChartTypes.BAR, categoryAxis, valueAxis);
        data.setBarDirection(BarDirection.COL); data.setBarGrouping(BarGrouping.CLUSTERED); data.setVaryColors(true);
        data.addSeries(categories, values).setTitle(seriesTitle, null);
        chart.plot(data);
    }

    private static void addPieChart(XSSFSheet sheet, String title, int categoryColumn, int valueColumn, int firstRow, int lastRow, int left, int top, int right, int bottom) {
        if (lastRow < firstRow) return;
        var chart = chart(sheet, title, left, top, right, bottom);
        var categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(firstRow, lastRow, categoryColumn, categoryColumn));
        var values = XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(firstRow, lastRow, valueColumn, valueColumn));
        var data = chart.createData(ChartTypes.PIE, null, null); data.setVaryColors(true);
        data.addSeries(categories, values).setTitle("Status", null);
        chart.plot(data);
    }

    private static void addLineChart(XSSFSheet sheet, String title, int categoryColumn, int createdColumn, int closedColumn, int firstRow, int lastRow, int left, int top, int right, int bottom) {
        if (lastRow < firstRow) return;
        var chart = chart(sheet, title, left, top, right, bottom);
        var categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        var valueAxis = chart.createValueAxis(AxisPosition.LEFT); valueAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        var categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, new CellRangeAddress(firstRow, lastRow, categoryColumn, categoryColumn));
        var data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, categoryAxis, valueAxis);
        var created = (XDDFLineChartData.Series) data.addSeries(categories, XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(firstRow, lastRow, createdColumn, createdColumn)));
        created.setTitle("Opprettet", null); created.setMarkerStyle(MarkerStyle.CIRCLE);
        var closed = (XDDFLineChartData.Series) data.addSeries(categories, XDDFDataSourcesFactory.fromNumericCellRange(sheet, new CellRangeAddress(firstRow, lastRow, closedColumn, closedColumn)));
        closed.setTitle("Lukket", null); closed.setMarkerStyle(MarkerStyle.DIAMOND);
        chart.plot(data);
    }

    private static XSSFChart chart(XSSFSheet sheet, String title, int left, int top, int right, int bottom) {
        var drawing = sheet.createDrawingPatriarch();
        var chart = drawing.createChart(drawing.createAnchor(0, 0, 0, 0, left, top, right, bottom));
        chart.setTitleText(title); chart.setTitleOverlay(false); chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
        return chart;
    }

    private static void row(XSSFSheet sheet, int index, CellStyle style, Object... values) { rowAt(sheet, index, 0, style, values); }

    private static void rowAt(XSSFSheet sheet, int index, int startColumn, CellStyle style, Object... values) {
        var row = sheet.getRow(index) == null ? sheet.createRow(index) : sheet.getRow(index);
        for (var i = 0; i < values.length; i++) cell(row, startColumn + i, style, values[i]);
    }

    private static void cell(XSSFSheet sheet, int rowIndex, int column, CellStyle style, Object value) {
        var row = sheet.getRow(rowIndex) == null ? sheet.createRow(rowIndex) : sheet.getRow(rowIndex);
        cell(row, column, style, value);
    }

    private static void cell(org.apache.poi.ss.usermodel.Row row, int column, CellStyle style, Object value) {
        var cell = row.createCell(column);
        if (value instanceof Number number) cell.setCellValue(number.doubleValue()); else cell.setCellValue(value == null ? "" : value.toString());
        if (style != null) cell.setCellStyle(style);
    }

    private static long count(List<Long> durations, long min, long max) { return durations.stream().filter(value -> value >= min && value <= max).count(); }
    private static long percent(List<Long> durations, long min, long max) { return durations.isEmpty() ? 0 : Math.round(count(durations, min, max) * 100.0 / durations.size()); }

    private static long percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) return 0;
        if (percentile <= 0) return sorted.getFirst();
        var index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private static String displayTime(Instant value) { return value.atZone(REPORT_ZONE).format(DATE_TIME); }
    private static String statusLabel(String value) { return switch (value) { case "IN_PROGRESS" -> "Pågår"; case "WAITING" -> "Venter"; case "ESCALATED" -> "Eskalert"; case "CLOSED" -> "Lukket"; default -> value; }; }
    private static String deviceLabel(String value) { return switch (value) { case "PHONE" -> "Telefon"; case "TABLET" -> "Nettbrett"; case "SMARTWATCH" -> "Smartklokke"; case "COMPUTER" -> "Datamaskin"; case "OTHER" -> "Annet"; default -> value; }; }
    private static String operatingSystemLabel(String value) { return switch (value) { case "IOS" -> "iOS"; case "ANDROID" -> "Android"; case "OTHER" -> "Annet"; default -> value; }; }
    private static Range range(ReportFilter filter) { return new Range(filter.from().atStartOfDay(REPORT_ZONE).toInstant().toString(), filter.to().plusDays(1).atStartOfDay(REPORT_ZONE).toInstant().toString()); }

    private record Range(String from, String to) {}
    private record ReportRow(String status, boolean urgent, Instant createdAt, boolean escalated) {}
    private record ClosedRow(long minutes, Instant closedAt) {}
    private record ReportData(List<ReportRow> tickets, List<ClosedRow> closed) {}
    private record TrendPoint(String label, long created, long closed) {}
    private record Styles(CellStyle title, CellStyle subtitle, CellStyle section, CellStyle header, CellStyle body, CellStyle stripe, CellStyle metricLabel, CellStyle metricValue, CellStyle percentage) {}
}
