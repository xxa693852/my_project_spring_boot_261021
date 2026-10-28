package student.jia.my_project_spring_boot_261021.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import student.jia.my_project_spring_boot_261021.entity.Registration;

public interface RegistrationRepository
        extends JpaRepository<Registration, Long> {

    // 查詢這位會員對這場活動的紀錄，包含已取消的紀錄
    Optional<Registration> findByMemberIdAndEventId(
            Long memberId, Long eventId);

    // 統計這場活動指定狀態的報名人數
    long countByEventIdAndStatus(Long eventId, String status);

    // 查詢這位會員的全部報名紀錄，最新報名排前面
    List<Registration> findByMemberIdOrderByRegisteredAtDesc(Long memberId);
}


