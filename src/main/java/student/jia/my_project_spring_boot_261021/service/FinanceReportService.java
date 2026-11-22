package student.jia.my_project_spring_boot_261021.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 同一個唯讀快照產生三張工作表；逐筆付款計算，避免一對多通知連接造成重複加總。 */
@Service
public class FinanceReportService {
    private final JdbcTemplate jdbc;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public FinanceReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Export export(LocalDate from, LocalDate through) {
        if (from == null || through == null || from.isAfter(through)
                || from.getYear() < 1000 || through.getYear() > 9998) {
            throw new IllegalArgumentException("請提供有效的起日、迄日，且起日不得晚於迄日。");
        }
        LocalDateTime begin = from.atStartOfDay();
        LocalDateTime end = through.plusDays(1).atStartOfDay();
        List<Map<String, Object>> payments = jdbc.queryForList("""
                SELECT p.id, p.merchant_order_no, p.provider_trade_no, p.amount, p.payment_status,
                    p.paid_at, p.refunded_at, p.refund_note, r.event_id, r.member_id, m.account, e.title
                FROM payments p JOIN registrations r ON r.id = p.registration_id
                JOIN members m ON m.id = r.member_id JOIN events e ON e.id = r.event_id
                WHERE p.paid_at IS NOT NULL OR p.refunded_at IS NOT NULL
                    OR p.payment_status IN ('REFUND_PENDING', 'REVIEW_REQUIRED') ORDER BY p.id
                """);
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT e.id, e.title, e.start_at, e.capacity,
                    (SELECT COUNT(*) FROM registrations r WHERE r.event_id = e.id AND r.status = 'REGISTERED') AS registered
                FROM events e ORDER BY e.start_at, e.id
                """);
        LocalDateTime generated = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        long receipts = 0, refunds = 0, pending = 0, reviewAmount = 0, reviewCount = 0;
        Map<Long, long[]> activityTotals = new LinkedHashMap<>();
        for (Map<String, Object> payment : payments) {
            long amount = ((Number) payment.get("amount")).longValue();
            long id = ((Number) payment.get("event_id")).longValue();
            long[] totals = activityTotals.get(id);
            if (totals == null) { totals = new long[4]; activityTotals.put(id, totals); }
            if (inRange(payment.get("paid_at"), begin, end)) { receipts += amount; totals[0] += amount; }
            if (inRange(payment.get("refunded_at"), begin, end)) { refunds += amount; totals[1] += amount; }
            if ("REFUND_PENDING".equals(payment.get("payment_status"))) { pending += amount; totals[2] += amount; }
            if ("REVIEW_REQUIRED".equals(payment.get("payment_status"))) { reviewCount++; reviewAmount += amount; totals[3] += amount; }
        }
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle header = book.createCellStyle();
            Font font = book.createFont(); font.setFontName("Microsoft JhengHei"); font.setFontHeightInPoints((short) 11); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            header.setWrapText(true); header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFont(font); header.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle text = book.createCellStyle();
            Font bodyFont = book.createFont(); bodyFont.setFontName("Microsoft JhengHei"); bodyFont.setFontHeightInPoints((short) 11);
            text.setFont(bodyFont); text.setWrapText(true); text.setVerticalAlignment(VerticalAlignment.CENTER);
            text.setBorderBottom(BorderStyle.HAIR); text.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            text.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            Sheet summary = sheet(book, "統計摘要", from, through, generated, header, text);
            row(summary, header, "項目", "金額（新台幣元）／筆數", "統計依據");
            row(summary, text, "區間收款總額", receipts, "依付款成功時間；含已確認收款但待人工核對的款項");
            row(summary, text, "區間內標記已退款金額", refunds, "依後台標記已退款時間；目前僅全額退款");
            row(summary, text, "收款減已退款", receipts - refunds, "尚未扣除待退款款項、場租、主持人費用、金流手續費及其他成本，不代表純利潤。");
            row(summary, text, "目前待退款總額", pending, "包含所有目前待退款訂單，不限查詢日期。已在金流處理退款、但尚未回本站登記的訂單，仍會列在待退款中。");
            row(summary, text, "目前已收款待核對金額", reviewAmount, "已收到款項，但系統無法直接確認為有效報名，需管理員核對並決定後續處理。");
            row(summary, text, "目前已收款待核對訂單數", reviewCount, "上述待核對款項對應的訂單筆數。");
            addNotes(summary, text);
            Sheet activity = sheet(book, "活動統計", from, through, generated, header, text);
            row(activity, header, "活動編號", "活動名稱", "開始時間", "名額上限", "目前已報名人數", "區間收款", "區間標記已退款", "目前待退款", "目前已收款待核對金額");
            for (Map<String, Object> event : events) {
                Long id = ((Number) event.get("id")).longValue();
                long[] totals = activityTotals.get(id);
                // 有選定區間的活動或款項，或目前待處理項目才列入。
                boolean selected = inRange(event.get("start_at"), begin, end);
                if (totals == null) { totals = new long[4]; }
                if (!selected && totals[0] == 0 && totals[1] == 0 && totals[2] == 0 && totals[3] == 0) { continue; }
                row(activity, text, id, event.get("title"), time(event.get("start_at")), event.get("capacity"), event.get("registered"), totals[0], totals[1], totals[2], totals[3]);
            }
            if (activity.getLastRowNum() > 3) {
                activity.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(3, activity.getLastRowNum(), 0, 8));
            }
            addNotes(activity, text);
            Sheet detail = sheet(book, "交易明細", from, through, generated, header, text);
            detail.createFreezePane(0, 5);
            detailSection(detail, "區間交易：付款或標記退款時間在區間內，同一訂單僅列一次", payments, begin, end, 0, header, text);
            detailSection(detail, "目前待退款清單：報表產生當下狀態，不受日期區間限制", payments, begin, end, 1, header, text);
            detailSection(detail, "目前已收款待核對清單：已收到款項但報名需人工確認；不限日期區間，勿重複加總", payments, begin, end, 2, header, text);
            addNotes(detail, text);
            book.write(output);
            return new Export(output.toByteArray(), "dnd-report_" + from + "_" + through + "_" + generated.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx");
        } catch (IOException failure) { throw new IllegalStateException("無法產生 Excel 報表。", failure); }
    }

    static boolean inRange(Object value, LocalDateTime begin, LocalDateTime end) {
        if (value == null) { return false; }
        LocalDateTime date;
        if (value instanceof Timestamp) { date = ((Timestamp) value).toLocalDateTime(); }
        else { date = (LocalDateTime) value; }
        return !date.isBefore(begin) && date.isBefore(end);
    }
    private static String time(Object value) {
        if (value == null) { return "—"; }
        if (value instanceof Timestamp) { return ((Timestamp) value).toLocalDateTime().format(TIME); }
        return ((LocalDateTime) value).format(TIME);
    }
    private Sheet sheet(Workbook book, String name, LocalDate from, LocalDate through, LocalDateTime generated, CellStyle header, CellStyle text) {
        Sheet sheet = book.createSheet(name);
        // 先設定最後使用的欄寬，才計算換行與列高；避免用預設窄欄把說明列撐到數百點。
        int[] widths;
        if ("統計摘要".equals(name)) { widths = new int[] {30, 24, 78}; }
        else if ("活動統計".equals(name)) { widths = new int[] {12, 42, 24, 12, 20, 20, 24, 20, 20}; }
        else { widths = new int[] {34, 42, 24, 24, 16, 18, 24, 24, 28, 32, 50}; }
        for (int i = 0; i < widths.length; i++) { sheet.setColumnWidth(i, widths[i] * 256); }
        sheet.setDisplayGridlines(false);
        sheet.setZoom(90);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setLandscape(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        CellStyle title = book.createCellStyle(); title.cloneStyleFrom(header);
        Font titleFont = book.createFont(); titleFont.setFontName("Microsoft JhengHei");
        titleFont.setFontHeightInPoints((short) 16); titleFont.setBold(true); titleFont.setColor(IndexedColors.WHITE.getIndex());
        title.setFont(titleFont);
        row(sheet, title, "D&D 活動平台｜" + name);
        row(sheet, text, "查詢區間", from + " ～ " + through + "（台北時間，含起訖日）");
        row(sheet, text, "產生時間", generated.format(TIME) + "（台北時間）");
        sheet.createFreezePane(0, 4);
        return sheet;
    }

    private void addNotes(Sheet sheet, CellStyle text) {
        sheet.createRow(sheet.getPhysicalNumberOfRows()).setHeightInPoints(12);
        row(sheet, text, "閱讀說明");
        row(sheet, text, "狀態快照", "已報名人數、待退款、已收款待核對款項為報表產生當下狀態，下載後不更新；待退款及已收款待核對款項不受日期範圍限制。活動人數不是實際出席或累計報名人次。");
        row(sheet, text, "退款時間說明", "退款金額依管理員在本站標記「已退款」的時間統計，不是金流實際處理或玩家收到退款的時間。玩家何時收到退款，仍以金流平台及發卡銀行的處理結果為準。");
        row(sheet, text, "金額注意事項", "「收款減已退款」還沒扣除待退款、場租、主持人費用、金流手續費及其他成本，不能當成純利潤。同一筆訂單可能同時出現在不同明細清單中，請勿重複加總。");
        row(sheet, text, "待核對款項", "已收到款項，但系統無法直接確認為有效報名。例如付款保留已逾時，付款成功通知才抵達，名額可能已釋出，因此需管理員核對。");
        row(sheet, text, "金額計算說明", "待核對清單包含報表產生時仍需處理的所有訂單，不限查詢日期。這些款項已依付款成功時間計入收款總額，不需另外加上。");
        sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(0, 3, -1, -1));
    }
    private void detailSection(Sheet sheet, String title, List<Map<String, Object>> payments, LocalDateTime begin, LocalDateTime end, int mode, CellStyle header, CellStyle text) {
        row(sheet, header, title);
        row(sheet, header, "訂單編號", "活動名稱", "會員編號／帳號", "付款狀態", "訂單金額", "本區間收款", "本區間標記已退款", "付款成功時間", "後台標記已退款時間", "金流交易編號", "內部退款說明");
        int count = 0;
        for (Map<String, Object> payment : payments) {
            boolean paid = inRange(payment.get("paid_at"), begin, end);
            boolean refunded = inRange(payment.get("refunded_at"), begin, end);
            boolean include;
            if (mode == 0) { include = paid || refunded; }
            else if (mode == 1) { include = "REFUND_PENDING".equals(payment.get("payment_status")); }
            else { include = "REVIEW_REQUIRED".equals(payment.get("payment_status")); }
            if (!include) { continue; }
            long amount = ((Number) payment.get("amount")).longValue();
            row(sheet, text, payment.get("merchant_order_no"), payment.get("title"), payment.get("member_id") + "／" + payment.get("account"), status((String) payment.get("payment_status")), amount,
                    paid ? amount : 0, refunded ? amount : 0, time(payment.get("paid_at")), time(payment.get("refunded_at")), payment.get("provider_trade_no"), payment.get("refund_note"));
            count++;
        }
        if (count == 0) { row(sheet, text, "沒有符合條件的紀錄。"); }
    }
    private String status(String status) {
        if ("PAID".equals(status)) { return "已付款"; }
        if ("REFUNDED".equals(status)) { return "後台已標記退款"; }
        if ("REFUND_PENDING".equals(status)) { return "待全額退款"; }
        if ("REVIEW_REQUIRED".equals(status)) { return "已收款待人工核對"; }
        return status;
    }
    private void row(Sheet sheet, CellStyle style, Object... values) {
        Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
        int columns = "統計摘要".equals(sheet.getSheetName()) ? 3 : 11;
        if ("活動統計".equals(sheet.getSheetName())) { columns = 9; }
        int mergeFrom = -1;
        if (values.length == 1) { mergeFrom = 0; }
        else if (values.length == 2) { mergeFrom = 1; }
        int maxLines = 1;
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            Cell cell = row.createCell(i);
            CellStyle chosen = style;
            if (row.getRowNum() > 2 && style.getFillPattern() == FillPatternType.NO_FILL) {
                chosen = bodyStyle(sheet.getWorkbook(), style, value instanceof Number, row.getRowNum() % 2 == 0);
            }
            cell.setCellStyle(chosen);
            if (value instanceof Number) { cell.setCellValue(((Number) value).doubleValue()); }
            else if (value != null) { cell.setCellValue(value.toString()); }
            if (value instanceof String) {
                int units = 0;
                String label = (String) value;
                for (int j = 0; j < label.length(); j++) {
                    if (label.charAt(j) > 255) { units += 2; } else { units++; }
                }
                int capacity = sheet.getColumnWidth(i) / 256 - 3;
                if (i == mergeFrom) {
                    for (int column = i + 1; column < columns; column++) { capacity += sheet.getColumnWidth(column) / 256; }
                }
                maxLines = Math.max(maxLines, (units + Math.max(1, capacity) - 1) / Math.max(1, capacity));
            }
        }
        if (mergeFrom >= 0 && mergeFrom < columns - 1) {
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(row.getRowNum(), row.getRowNum(), mergeFrom, columns - 1));
        }
        row.setHeightInPoints(Math.min(409, Math.max(26, maxLines * 17 + 8)));
        if (row.getRowNum() == 0) { row.setHeightInPoints(34); }
    }

    private CellStyle bodyStyle(Workbook book, CellStyle original, boolean number, boolean stripe) {
        HorizontalAlignment alignment = number ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT;
        short color = stripe ? IndexedColors.GREY_25_PERCENT.getIndex() : IndexedColors.WHITE.getIndex();
        for (int i = 0; i < book.getNumCellStyles(); i++) {
            CellStyle existing = book.getCellStyleAt(i);
            if (existing.getFillPattern() == FillPatternType.SOLID_FOREGROUND
                    && existing.getFillForegroundColor() == color && existing.getAlignment() == alignment
                    && existing.getDataFormat() == original.getDataFormat()) { return existing; }
        }
        CellStyle result = book.createCellStyle();
        result.cloneStyleFrom(original); result.setAlignment(alignment);
        result.setFillPattern(FillPatternType.SOLID_FOREGROUND); result.setFillForegroundColor(color);
        return result;
    }
    public record Export(byte[] bytes, String filename) {}
}
