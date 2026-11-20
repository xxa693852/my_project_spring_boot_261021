package student.jia.my_project_spring_boot_261021;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 鎖定正式環境已使用的 V1～V13，避免補註解時破壞 Flyway 校驗。 */
class HistoricalMigrationIntegrityTests {
    @Test
    void appliedMigrationsKeepTheirOriginalFlywayChecksums() throws Exception {
        verify("V10__create_comment_moderation_notifications.sql", -1823211928);
        verify("V11__add_event_list_visibility.sql", 1194643249);
        verify("V12__add_event_registration_fee.sql", -2078997865);
        verify("V13__create_event_payments.sql", -675953651);
        verify("V1__create_core_tables.sql", 201806090);
        verify("V2__create_auth_tokens_table.sql", 456104884);
        verify("V3__create_password_reset_tokens_table.sql", -1703907315);
        verify("V4__add_event_cover_and_tags.sql", -1720291107);
        verify("V5__add_event_cancellation_notifications.sql", 651865285);
        verify("V6__add_event_reminder_deliveries.sql", -2132308049);
        verify("V7__add_member_suspensions.sql", -653567586);
        verify("V8__add_suspension_notification_status.sql", -946167802);
        verify("V9__create_event_comments.sql", -74924061);
    }

    private void verify(String filename, int expected) throws Exception {
        try (var input = getClass().getResourceAsStream("/db/migration/" + filename)) {
            assertNotNull(input, filename);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            CRC32 crc = new CRC32();
            for (String line : sql.replace("\uFEFF", "").split("\r\n|\r|\n")) {
                crc.update(line.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(expected, (int) crc.getValue(), filename);
        }
    }
}
