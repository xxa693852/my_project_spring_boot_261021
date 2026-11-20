package student.jia.my_project_spring_boot_261021.response;

import java.util.List;

/**
 * 活動分頁回應，包含當頁活動、頁碼、總頁數與總筆數。
 */
public record EventPageResponse(
        List<EventResponse> events,
        int page,
        int totalPages,
        long totalEvents
) {
}
