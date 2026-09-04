package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.ReportFilter;
import no.telefonhjelp.domain.ApiModels.ReportSummary;
import no.telefonhjelp.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service) { this.service = service; }
    @GetMapping("/summary") ReportSummary summary(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required=false) Long employeeId, @RequestParam(required=false) String category) { return service.summary(new ReportFilter(from,to,employeeId,category)); }
    @GetMapping("/xlsx") ResponseEntity<byte[]> xlsx(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required=false) Long employeeId, @RequestParam(required=false) String category) throws Exception { var bytes=service.workbook(new ReportFilter(from,to,employeeId,category)); return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=telefonhjelp-rapport.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes); }
}

