package student.jia.my_project_spring_boot_261021.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import student.jia.my_project_spring_boot_261021.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {


 // 依活動 ID 和狀態查詢
    Optional<Event> findByIdAndStatus(Long id, String status);


    
}