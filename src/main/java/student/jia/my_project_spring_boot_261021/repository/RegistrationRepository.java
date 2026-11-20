package student.jia.my_project_spring_boot_261021.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.entity.Event;

/**
 * 報名資料查詢介面，提供個人紀錄、時段衝突與名額統計。
 * 已確認報名與仍有效的待付款保留分開計算，供 Service 合計占用名額。
 */
public interface RegistrationRepository
        extends JpaRepository<Registration, Long> {

    // 查詢這位會員對這場活動的紀錄，包含已取消的紀錄
    Optional<Registration> findByMemberIdAndEventId(
            Long memberId, Long eventId);

    @Query("""
            SELECT e FROM Registration r, Event e
            WHERE r.eventId = e.id AND r.memberId = :memberId
              AND r.eventId <> :eventId AND e.status = 'OPEN'
              AND (r.status = 'REGISTERED' OR (r.status = 'PENDING_PAYMENT' AND r.paymentExpiresAt > :now))
              AND e.startAt < :endAt AND e.endAt > :startAt
            ORDER BY e.startAt, e.id
            """)
    // 查詢同會員的其他有效報名與待付款保留；兩活動僅首尾相接時不視為時段重疊。
    List<Event> findOverlappingEvents(
            @Param("memberId") Long memberId, @Param("eventId") Long eventId,
            @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt,
            @Param("now") LocalDateTime now);

    // 只計入尚在期限內的保留，逾時後下一次查詢就能讓出名額。
    @Query("""
            SELECT COUNT(r) FROM Registration r WHERE r.eventId = :eventId
              AND r.status = 'PENDING_PAYMENT' AND r.paymentExpiresAt > :now
            """)
    long countActiveHolds(@Param("eventId") Long eventId, @Param("now") LocalDateTime now);

    // 取消整場活動時，查出仍有效的報名，逐筆更新狀態與取消時間。
    List<Registration> findByEventIdAndStatus(Long eventId, String status);

    // 統計這場活動指定狀態的報名人數
    long countByEventIdAndStatus(Long eventId, String status);

    // 查詢這位會員的全部報名紀錄，最新報名排前面
    List<Registration> findByMemberIdOrderByRegisteredAtDesc(Long memberId);
}


