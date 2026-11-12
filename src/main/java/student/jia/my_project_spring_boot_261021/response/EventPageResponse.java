package student.jia.my_project_spring_boot_261021.response;

import java.util.List;

public record EventPageResponse(
        List<EventResponse> events,
        int page,
        int totalPages,
        long totalEvents
) {
}
