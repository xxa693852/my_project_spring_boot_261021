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

public interface EventCommentRepository extends JpaRepository<EventComment, Long> {

    @EntityGraph(attributePaths = "author")
    Page<EventComment> findByEventIdAndHiddenReasonIsNullOrderByIdDesc(Long eventId, Pageable page);

    @EntityGraph(attributePaths = "author")
    Page<EventComment> findByEventIdOrderByIdDesc(Long eventId, Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EventComment c WHERE c.eventId = :eventId AND c.id = :commentId")
    Optional<EventComment> findForUpdate(Long eventId, Long commentId);
}
