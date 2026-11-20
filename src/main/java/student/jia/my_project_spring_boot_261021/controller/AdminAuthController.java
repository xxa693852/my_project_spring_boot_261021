package student.jia.my_project_spring_boot_261021.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.service.AdminAuthService;

/** 管理員專用登入入口；不使用會員登入 API，也不提供保持登入選項。 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final AdminAuthService auth;

    public AdminAuthController(AdminAuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public AdminAuthService.AdminIdentity login(@Valid @RequestBody MemberLoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return auth.login(input, request);
    }

    @GetMapping("/me")
    public AdminAuthService.AdminIdentity current(HttpServletRequest request) {
        return auth.require(request, false);
    }

    @PostMapping("/activity")
    public AdminAuthService.AdminIdentity activity(HttpServletRequest request) {
        // 攔截器已驗證 CSRF 並更新活動時間；此處只讀取更新後的結果。
        return auth.require(request, false);
    }

    @PostMapping("/logout")
    public ApiResponse logout(HttpServletRequest request) {
        auth.logout(request);
        return new ApiResponse("已登出管理後台。");
    }
}
