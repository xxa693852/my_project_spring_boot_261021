package student.jia.my_project_spring_boot_261021.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import student.jia.my_project_spring_boot_261021.entity.RegistrationCancellation;

public interface RegistrationCancellationRepository extends JpaRepository<RegistrationCancellation, Long> {
    List<RegistrationCancellation> findByEventIdOrderByCancelledAtDescIdDesc(Long eventId);
}
