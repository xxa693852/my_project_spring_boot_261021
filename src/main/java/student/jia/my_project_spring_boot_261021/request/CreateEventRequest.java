package student.jia.my_project_spring_boot_261021.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter 
public class CreateEventRequest {
    
    @NotBlank(message = "活動名稱不能空白")
    @Size(max = 100, message = "活動名稱最多100字")
    private String title;

    @NotBlank(message = "活動描述不能為空") //不能為空白
    private String description;

    @NotBlank(message = "主持人名稱不能空白")
    @Size(max = 50, message = "主持人名稱超過字數限制")
    private String hostName;

    @NotBlank(message = "地點不能空白")
    @Size(max = 255, message = "地點超過字數限制") 
    private String location;

    @NotNull(message = "開始時間不能空白")
    private LocalDateTime startAt;

    @NotNull(message = "結束時間不能空白")
    private LocalDateTime endAt;

    @NotNull(message = "報名截止時間不能空白")
    private LocalDateTime registrationDeadline;
    
    @NotNull(message = "人數上限不能空白")
    @Min(value = 1, message = "人數上限至少1人")
    private Integer capacity;

    @NotNull(message = "請指定是否新手友善")
    private Boolean beginnerFriendly;

}
// 欄位	Java 型別	驗證
// title	String	必填，最多 100 字
// description	String	必填
// hostName	String	必填，最多 50 字
// location	String	必填，最多 255 字
// startAt	LocalDateTime	必填
// endAt	LocalDateTime	必填
// registrationDeadline	LocalDateTime	必填
// capacity	Integer	必填，至少 1 人
// beginnerFriendly	Boolean	必填，可傳 false