package student.jia.my_project_spring_boot_261021.repository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import student.jia.my_project_spring_boot_261021.entity.Event;
import java.util.List;


import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * 活動查詢與搜尋介面；Page 回傳當頁內容與總筆數，List 則回傳查詢結果清單。
 * 需要修改或檢查名額的流程使用鎖定查詢，鎖會持續到呼叫端交易結束。
 */
public interface EventRepository extends JpaRepository<Event, Long> {


 // 依活動 ID 和狀態查詢
    Optional<Event> findByIdAndStatus(Long id, String status);


// 鎖定指定活動，直到呼叫端的交易結束
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    // 查詢指定狀態的活動，開始時間由近到遠排列
List<Event> findByStatusOrderByStartAtAsc(String status);

    // 公開列表用：排除隱藏與已結束活動，並以開始時間、ID 固定排序。
    List<Event> findByStatusAndHiddenFromPublicListFalseAndEndAtAfterOrderByStartAtAscIdAsc(
            String status, LocalDateTime now);

    @Query("""
            SELECT e FROM Event e
            WHERE e.status = 'OPEN'
              AND e.hiddenFromPublicList = false
              AND e.endAt > :now
              AND (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(:keyword) ESCAPE '!'
                   OR LOWER(e.location) LIKE LOWER(:keyword) ESCAPE '!')
              AND (:startFrom IS NULL OR e.startAt >= :startFrom)
              AND (:until IS NULL OR e.startAt < :until)
              AND (:beginnerOnly = false OR e.beginnerFriendly = true)
              AND (:availableOnly = false OR (
                  (e.registrationFee = 0 OR :paymentsReady = true)
                  AND e.registrationDeadline > :now
                  AND e.capacity > (SELECT COUNT(r) FROM Registration r
                       WHERE r.eventId = e.id AND (r.status = 'REGISTERED'
                           OR (r.status = 'PENDING_PAYMENT' AND r.paymentExpiresAt > :now)))
              ))
            """)
    // 公開搜尋會先套用顯示、日期與報名條件再分頁；可報名條件也計入有效付款保留。
    Page<Event> searchPublic(@Param("keyword") String keyword,
            @Param("startFrom") LocalDateTime from, @Param("until") LocalDateTime until,
            @Param("beginnerOnly") boolean beginnerOnly, @Param("availableOnly") boolean availableOnly,
            @Param("paymentsReady") boolean paymentsReady,
            @Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            SELECT e FROM Event e
            WHERE (:keyword IS NULL OR LOWER(e.title) LIKE LOWER(:keyword) ESCAPE '!'
                   OR LOWER(e.location) LIKE LOWER(:keyword) ESCAPE '!'
                   OR LOWER(e.hostName) LIKE LOWER(:keyword) ESCAPE '!')
              AND (:editableOnly = false OR (e.status = 'OPEN' AND e.startAt > :now))
            """)
    // 管理員搜尋包含主持人關鍵字；editableOnly 限制為開放且尚未開始的活動。
    Page<Event> searchAdmin(@Param("keyword") String keyword,
            @Param("editableOnly") boolean editableOnly, @Param("now") LocalDateTime now,
            Pageable pageable);

}
