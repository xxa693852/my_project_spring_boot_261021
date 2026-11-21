package student.jia.my_project_spring_boot_261021.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "event_comments")
public class EventComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member author;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "hidden_reason", length = 200)
    private String hiddenReason;

    protected EventComment() {
    }

    public EventComment(Long eventId, Member author, String body, LocalDateTime createdAt) {
        this.eventId = eventId;
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
    }

    public void edit(String body) {
        this.body = body;
    }

    public void hide(String reason) {
        this.hiddenReason = reason;
    }

    public void restore() {
        this.hiddenReason = null;
    }
}
