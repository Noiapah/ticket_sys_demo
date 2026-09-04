package no.telefonhjelp.service;

import no.telefonhjelp.PhoneSupportApplication;
import no.telefonhjelp.domain.ApiModels.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void workbookOmitsCustomerAndNarrativeFields() throws Exception {
        var actor = employees.list().stream().findFirst().orElseGet(() -> employees.create("Markus"));
        tickets.create(new TicketDraft("HEMMELIG NAVN", "99123456", DeviceType.PHONE, "Apple", "iPhone 15", OperatingSystem.IOS, "E-post", "HEMMELIG BESKRIVELSE", actor.id()));
        var filter = new ReportFilter(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), null, null);
        var bytes = reports.workbook(filter);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var rendered = new StringBuilder();
            workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> rendered.append(cell.toString()).append('\n'))));
            assertThat(rendered.toString()).doesNotContain("HEMMELIG NAVN", "HEMMELIG BESKRIVELSE", "99123456");
            assertThat(workbook.getSheet("Saker").getRow(0).getPhysicalNumberOfCells()).isEqualTo(10);
        }
    }
}
