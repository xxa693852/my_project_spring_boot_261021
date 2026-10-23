package student.jia.my_project_spring_boot_261021.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import student.jia.my_project_spring_boot_261021.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByAccount(String account); //帳戶存在

    boolean existsByEmail(String email);  // 透過電子郵件
}
