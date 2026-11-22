package student.jia.my_project_spring_boot_261021.controller;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import student.jia.my_project_spring_boot_261021.service.FinanceReportService;

/** /admin/** 由既有後台攔截器驗證，不接受前台會員 Token 取代管理員 Session。 */
@RestController
public class FinanceReportController {
    private final FinanceReportService reports;
    public FinanceReportController(FinanceReportService reports) { this.reports = reports; }
    @GetMapping("/admin/reports/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate through) {
        FinanceReportService.Export result = reports.export(from, through);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.filename()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(result.bytes());
    }
}
