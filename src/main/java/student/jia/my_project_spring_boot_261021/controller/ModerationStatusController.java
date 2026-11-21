package student.jia.my_project_spring_boot_261021.controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import student.jia.my_project_spring_boot_261021.service.AiContentReview;
/** /admin 路徑沿用管理員 Session 驗證；一般會員不能讀取運行狀態。 */
@RestController
public class ModerationStatusController {
 private final AiContentReview review;
 public ModerationStatusController(AiContentReview review) { this.review=review; }
 @GetMapping("/admin/moderation/status")
 public ResponseEntity<AiContentReview.Health> status() {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(review.status());
 }
}
