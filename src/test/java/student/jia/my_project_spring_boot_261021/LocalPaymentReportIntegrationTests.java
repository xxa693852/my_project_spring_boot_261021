package student.jia.my_project_spring_boot_261021;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import student.jia.my_project_spring_boot_261021.entity.*;
import student.jia.my_project_spring_boot_261021.repository.*;
import student.jia.my_project_spring_boot_261021.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 只用本機專用測試 schema；建立資料、驗證付款簽章與真正下載，再回滾，不呼叫藍新。 */
@SpringBootTest(properties = {"spring.jpa.show-sql=false"})
@Transactional
class LocalPaymentReportIntegrationTests {
    @MockitoBean(name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    ScheduledAnnotationBeanPostProcessor scheduler;
    @MockitoBean MailService mail;
    @MockitoBean LineNotificationClient line;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired MemberRepository members;
    @Autowired EventRepository events;
    @Autowired PaymentRepository payments;
    @Autowired RegistrationRepository registrations;
    @Autowired RegistrationService registrationService;
    @Autowired PaymentLifecycle lifecycle;
    @Autowired FinanceReportService reports;
    @Autowired PasswordEncoder encoder;
    @Autowired WebApplicationContext context;
    private final List<Long> eventIds = new ArrayList<>();
    private final List<Long> memberIds = new ArrayList<>();
    private static final String KEY = "00000000000000000000000000000000";
    private static final String IV = "0000000000000000";
    private static final String PASSWORD = "LocalTestPassword123!";

    @BeforeEach void requireLocalTestDatabase() {
        assertEquals("dnd_event_prod_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
    }
    private Member member(String role) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Member member = new Member("localqa_" + id, encoder.encode(PASSWORD), "本機報表測試", id + "@example.invalid");
        member.setRole(role); members.saveAndFlush(member); memberIds.add(member.getId()); return member;
    }
    private Event event(Long admin, String title, int fee) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        Event event = new Event("[本機測試] " + title, "模擬付款，測試結束回滾", "測試主持", "測試場所", now.plusDays(2), now.plusDays(2).plusHours(2), now.plusDays(1), 3, true, admin);
        event.setRegistrationFee(fee); event.setStatus("OPEN"); events.saveAndFlush(event); eventIds.add(event.getId()); return event;
    }
    private Payment pay(Member player, Event event) throws Exception {
        NewebPayService gateway = new NewebPayService(registrationService, lifecycle, "MSDEMO", KEY, IV, "https://local-payment-test.invalid");
        NewebPayService.PaymentForm form = gateway.createForm(player.getId(), event.getId(), event.getRegistrationFee());
        assertEquals("https://ccore.newebpay.com/MPG/mpg_gateway", form.gatewayUrl());
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
        String params = new String(decrypt.doFinal(HexFormat.of().parseHex(form.tradeInfo())), StandardCharsets.UTF_8);
        assertTrue(params.contains("local-payment-test.invalid"));
        assertFalse(params.contains("ngrok"));
        Registration registration = registrations.findByMemberIdAndEventId(player.getId(), event.getId()).get();
        Payment payment = payments.findFirstByRegistrationIdOrderByIdDesc(registration.getId()).get();
        String plain = "Status=SUCCESS&MerchantID=MSDEMO&Amt=" + payment.getAmount() + "&TradeNo=LOCAL" + payment.getId()
                + "&MerchantOrderNo=" + payment.getMerchantOrderNo() + "&RespondCode=00&PaymentType=CREDIT";
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"), new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
        String info = HexFormat.of().formatHex(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        String input = "HashKey=" + KEY + "&" + info + "&HashIV=" + IV;
        String sha = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> gateway.handleNotify(info, "0".repeat(64)));
        gateway.handleNotify(info, sha); gateway.handleNotify(info, sha);
        entityManager.flush();
        assertEquals("PAID", payment.getPaymentStatus());
        return payment;
    }
    private double amount(byte[] bytes, String label) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (Row row : book.getSheetAt(0)) {
                if (row.getCell(0) != null && label.equals(row.getCell(0).toString())) return row.getCell(1).getNumericCellValue();
            }
        }
        throw new AssertionError("找不到 " + label);
    }
    @Test void localPaymentsRefundAndCompletedActivityAppearInRealReportDownload() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        LocalDate from = today.withDayOfMonth(1), through = today.withDayOfMonth(today.lengthOfMonth());
        byte[] before = reports.export(from, through).bytes();
        Member admin = member("ADMIN");
        Event paidEvent = event(admin.getId(), "保留付款", 600);
        Event refundEvent = event(admin.getId(), "登記退款", 400);
        Event pendingEvent = event(admin.getId(), "等待退款", 200);
        Member first = member("MEMBER"), second = member("MEMBER"), third = member("MEMBER");
        pay(first, paidEvent); Payment refund = pay(second, refundEvent); pay(third, pendingEvent);
        registrationService.cancel(second.getId(), refundEvent.getId());
        lifecycle.recordRefund(admin.getId(), refundEvent.getId(), refund.getId(), "本機模擬，沒有真實金流退款");
        registrationService.cancel(third.getId(), pendingEvent.getId());
        entityManager.flush();
        byte[] after = reports.export(from, through).bytes();
        assertEquals(1200, amount(after,"區間收款總額") - amount(before,"區間收款總額"));
        assertEquals(400, amount(after,"區間內標記已退款金額") - amount(before,"區間內標記已退款金額"));
        assertEquals(200, amount(after,"目前待退款總額") - amount(before,"目前待退款總額"));
        // 只調整本測試建立的場次模擬活動結束，證明收款不必等待活動開始。
        paidEvent.setStartAt(today.minusDays(1).atTime(10,0)); paidEvent.setEndAt(today.minusDays(1).atTime(12,0)); paidEvent.setRegistrationDeadline(today.minusDays(2).atTime(10,0)); paidEvent.setStatus("CLOSED");
        entityManager.flush();
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        AdminTestSession session = new AdminTestSession(mvc, admin, PASSWORD);
        byte[] download = mvc.perform(get("/admin/reports/export").param("from",from.toString()).param("through",through.toString()).with(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals(amount(after,"區間收款總額"), amount(download,"區間收款總額"));
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(download))) {
            assertEquals(3, book.getNumberOfSheets());
            boolean found = false;
            for (Row row : book.getSheetAt(1)) {
                if (row.getCell(1) != null && paidEvent.getTitle().equals(row.getCell(1).toString())) { found = true; assertEquals(600,row.getCell(5).getNumericCellValue()); }
            }
            assertTrue(found);
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM refund_notifications WHERE payment_id=?", Integer.class, refund.getId()));
        Path path = Path.of("target/report-preview/local-verified.xlsx"); Files.createDirectories(path.getParent()); Files.write(path, download);
    }
    @AfterTransaction void confirmRollback() {
        for (Long id : eventIds) assertFalse(events.existsById(id));
        for (Long id : memberIds) assertFalse(members.existsById(id));
    }
}
