package student.jia.my_project_spring_boot_261021.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import student.jia.my_project_spring_boot_261021.entity.Event;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, Long> {


 // 依活動 ID 和狀態查詢
    Optional<Event> findByIdAndStatus(Long id, String status);


// 鎖定指定活動，直到呼叫端的交易結束
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    
}