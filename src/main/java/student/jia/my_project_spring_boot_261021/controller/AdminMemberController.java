package student.jia.my_project_spring_boot_261021.controller;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.service.AdminMemberService;

@RestController
@RequestMapping("/admin/members")
public class AdminMemberController {
    private final AdminMemberService service;

    public AdminMemberController(AdminMemberService service) {
        this.service = service;
    }

    @GetMapping
    public AdminMemberService.MemberPage search(
            @RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long actor,
            @RequestParam(defaultValue = "") String query, @RequestParam(defaultValue = "1") int page) {
        return service.search(actor, query, page);
    }

    @GetMapping("/{id}")
    public AdminMemberService.MemberDetails details(
            @RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long actor, @PathVariable Long id) {
        return service.details(actor, id);
    }

    @PostMapping("/{id}/suspension")
    public ApiResponse changeSuspension(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long actor,
            @PathVariable Long id, @Valid @RequestBody SuspensionRequest request) {
        service.changeSuspension(actor, id, request.action(), request.until(), request.reason());
        return new ApiResponse("已更新會員停權設定");
    }

    public record SuspensionRequest(@NotBlank String action, LocalDateTime until,
            @NotBlank @Size(max = 500) String reason) {}
}
