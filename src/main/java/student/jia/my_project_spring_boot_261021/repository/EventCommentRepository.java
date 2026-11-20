package student.jia.my_project_spring_boot_261021.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import student.jia.my_project_spring_boot_261021.entity.EventComment;

/**
 * 活動留言查詢介面，區分公開留言與含隱藏留言的查詢。
 * @EntityGraph 同時載入作者資料，供回應使用作者名稱。
 */
public interface EventCommentRepository extends JpaRepository<EventComment, Long> {

    // 公開留言分頁：只查沒有隱藏原因的留言，較新的留言排在前面。
    @EntityGraph(attributePaths = "author")
    Page<EventComment> findByEventIdAndHiddenReasonIsNullOrderByIdDesc(Long eventId, Pageable page);

    // 查詢活動全部留言，包含隱藏留言，供有權限的流程使用。
    @EntityGraph(attributePaths = "author")
    Page<EventComment> findByEventIdOrderByIdDesc(Long eventId, Pageable page);

    // 依活動及留言 ID 查詢並鎖定，避免同時刪除或調整同一則留言。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EventComment c WHERE c.eventId = :eventId AND c.id = :commentId")
    Optional<EventComment> findForUpdate(Long eventId, Long commentId);
}
