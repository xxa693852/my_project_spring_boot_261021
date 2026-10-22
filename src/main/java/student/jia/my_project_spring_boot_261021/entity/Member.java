package student.jia.my_project_spring_boot_261021.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity //這句讓 Member 成為 Entity
@Table(name = "members")
public class Member {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //這個 ID 交給資料庫自動遞增（Auto Increment）來產生，Java 程式不需要（也不應該）手動傳入 ID 值。
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String account;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String password; // 存放密碼雜湊值

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    @Column(nullable = false, length = 20)
    private String role = "MEMBER"; // 會員角色：MEMBER、ADMIN

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE"; // 帳號狀態：ACTIVE、DISABLED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Member() { //無參數建構子，JPA需要它
    }


    public  Member(String account,String  password,String name,String email){
        this.account = account;
        this.password = password;
        this.name = name;
        this.email = email;
    }
  // @PrePersist: 新建時自動初始化建立與更新時間
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    // @PreUpdate: 修改時自動刷新最後更新時間
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
