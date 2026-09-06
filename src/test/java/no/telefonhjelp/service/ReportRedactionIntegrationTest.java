package no.telefonhjelp.service;

import no.telefonhjelp.PhoneSupportApplication;
import no.telefonhjelp.domain.ApiModels.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PhoneSupportApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportRedactionIntegrationTest {
    @TempDir static Path data;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("phone.support.data-dir", data::toString); registry.add("spring.main.web-application-type", () -> "none"); }
    @Autowired EmployeeService employees;
    @Autowired TicketService tickets;
    @Autowired ReportService reports;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationAddsRepresentativeDemoData() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE name IN ('Amalie','Henrik','Silje','Tobias','Mathilde','Sander','Nora','Eirik')", Long.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE name IN ('Nora','Eirik') AND active=0", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE substr(description,1,6)='[DEMO]'", Long.class)).isEqualTo(140);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE substr(description,1,6)='[DEMO]' AND status='CLOSED'", Long.class)).isEqualTo(84);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE substr(description,1,6)='[DEMO]' AND status<>'CLOSED'", Long.class)).isEqualTo(56);
    }

    @Test
    void workbookOmitsCustomerAndNarrativeFields() throws Exception {
        var actor = employees.list().stream().findFirst().orElseGet(() -> employees.create("Markus"));
        tickets.create(new TicketDraft("HEMMELIG NAVN", "99123456", DeviceType.PHONE, "Apple", "iPhone 15", "", OperatingSystem.IOS, "E-post", "HEMMELIG BESKRIVELSE", actor.id()));
        var filter = new ReportFilter(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), null, null);
        var bytes = reports.workbook(filter);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var rendered = new StringBuilder();
            workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> rendered.append(cell.toString()).append('\n'))));
            assertThat(rendered.toString()).doesNotContain("HEMMELIG NAVN", "HEMMELIG BESKRIVELSE", "99123456");
            assertThat(workbook.getSheet("Saker").getRow(0).getPhysicalNumberOfCells()).isEqualTo(13);
            assertThat(workbook.getSheet("Sammendrag").getDrawingPatriarch().getCharts()).hasSize(2);
            assertThat(workbook.getSheet("Kategorier").getDrawingPatriarch().getCharts()).hasSize(1);
            assertThat(workbook.getSheet("Trender").getDrawingPatriarch().getCharts()).hasSize(1);
            assertThat(workbook.getNumberOfSheets()).isEqualTo(7);
        }
    }
}
