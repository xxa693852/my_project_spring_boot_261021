package student.jia.my_project_spring_boot_261021.service;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinanceReportTests {
    private Map<String, Object> payment(long id, String status, String paid, String refunded) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id); row.put("event_id", 11L); row.put("member_id", 7L);
        row.put("merchant_order_no", "ORDER" + id); row.put("title", "=1+1"); row.put("account", "player");
        row.put("amount", 1000); row.put("payment_status", status);
        if (paid != null) row.put("paid_at", LocalDateTime.parse(paid));
        if (refunded != null) row.put("refunded_at", LocalDateTime.parse(refunded));
        return row;
    }
    private FinanceReportService service(List<Map<String, Object>> payments) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(payments, List.of(Map.of("id", 11L, "title", "冒險團", "start_at", LocalDateTime.of(2026,12,25,12,0), "capacity", 5, "registered", 2)));
        return new FinanceReportService(jdbc);
    }
    @Test void crossPeriodRefundsAndCurrentSnapshotsDoNotDoubleCount() throws Exception {
        List<Map<String,Object>> rows = List.of(
            payment(1,"REFUNDED","2026-11-20T10:00:00","2026-12-03T10:00:00"),
            payment(2,"REVIEW_REQUIRED","2026-12-31T23:59:59",null),
            payment(3,"REFUND_PENDING","2026-10-01T10:00:00",null),
            payment(4,"PAID","2027-01-01T00:00:00",null),
            payment(5,"REVIEW_REQUIRED",null,null));
        FinanceReportService.Export export = service(rows).export(LocalDate.of(2026,12,1),LocalDate.of(2026,12,31));
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(export.bytes()))) {
            assertEquals(3,book.getNumberOfSheets());
            Sheet summary=book.getSheetAt(0);
            assertEquals(1000,summary.getRow(4).getCell(1).getNumericCellValue());
            assertEquals(1000,summary.getRow(5).getCell(1).getNumericCellValue());
            assertEquals(0,summary.getRow(6).getCell(1).getNumericCellValue());
            assertEquals(1000,summary.getRow(7).getCell(1).getNumericCellValue());
            assertEquals(2000,summary.getRow(8).getCell(1).getNumericCellValue());
            assertEquals(2,summary.getRow(9).getCell(1).getNumericCellValue());
            // 頁首不可再次把資料推到畫面外；一般列高、凍結列、篩選亦需可用。
            for (Sheet sheet : book) {
                float total = 0;
                for (int i = 0; i < 3; i++) {
                    assertTrue(sheet.getRow(i).getHeightInPoints() <= 60);
                    total += sheet.getRow(i).getHeightInPoints();
                }
                assertTrue(total < 130);
                assertFalse(sheet.isDisplayGridlines());
            }
            assertNotNull(book.getSheetAt(1).getCTWorksheet().getAutoFilter());
            Sheet detail=book.getSheetAt(2);
            assertEquals("ORDER1",detail.getRow(5).getCell(0).getStringCellValue());
            assertEquals(0,detail.getRow(5).getCell(5).getNumericCellValue());
            assertEquals(1000,detail.getRow(5).getCell(6).getNumericCellValue());
            assertEquals(CellType.STRING,detail.getRow(5).getCell(1).getCellType());
            assertEquals("=1+1",detail.getRow(5).getCell(1).getStringCellValue());
            for (Sheet sheet : book) assertEquals(summary.getRow(2).getCell(1).getStringCellValue(),sheet.getRow(2).getCell(1).getStringCellValue());
        }
        Files.createDirectories(Path.of("target/report-preview"));
        Files.write(Path.of("target/report-preview/sample.xlsx"),export.bytes());
    }
    @Test void emptyReportAndInvalidRange() throws Exception {
        FinanceReportService service=service(List.of());
        try (XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(service.export(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30)).bytes()))) {
            assertEquals(0,book.getSheetAt(0).getRow(4).getCell(1).getNumericCellValue());
            assertEquals("沒有符合條件的紀錄。",book.getSheetAt(2).getRow(5).getCell(0).getStringCellValue());
        }
        assertThrows(IllegalArgumentException.class,()->service.export(LocalDate.of(2026,12,2),LocalDate.of(2026,12,1)));
        assertThrows(IllegalArgumentException.class,()->service.export(null,LocalDate.of(2026,12,1)));
    }
    @Test void exactDateBoundaries() {
        LocalDateTime begin=LocalDateTime.of(2026,12,1,0,0),end=LocalDateTime.of(2027,1,1,0,0);
        assertTrue(FinanceReportService.inRange(begin,begin,end));
        assertTrue(FinanceReportService.inRange(end.minusNanos(1),begin,end));
        assertFalse(FinanceReportService.inRange(end,begin,end));
        assertFalse(FinanceReportService.inRange(begin.minusNanos(1),begin,end));
    }
}
