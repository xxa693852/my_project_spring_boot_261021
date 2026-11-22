package student.jia.my_project_spring_boot_261021.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.service.AdminRegistrationService;

@RestController
@RequestMapping("/admin/events/{eventId}/registrations")
public class AdminRegistrationController {
    private final AdminRegistrationService service;
    public AdminRegistrationController(AdminRegistrationService service) {
        this.service = service;
    }

    @GetMapping
    public AdminRegistrationService.Roster list(
            @RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long operatorId,
            @PathVariable Long eventId) {
        return service.list(operatorId, eventId);
    }

    @PostMapping("/{registrationId}/cancel")
    public ApiResponse cancel(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long operatorId,
            @PathVariable Long eventId, @PathVariable Long registrationId,
            @Valid @RequestBody CancelRequest request) {
        service.cancel(operatorId, eventId, registrationId, request.reason());
        return new ApiResponse("已取消報名並釋出名額；已付款者請至付款紀錄處理待退款。");
    }
    public record CancelRequest(@NotBlank @Size(max = 255) String reason) {}
}
