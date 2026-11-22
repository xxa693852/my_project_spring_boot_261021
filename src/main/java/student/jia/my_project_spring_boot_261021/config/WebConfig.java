package student.jia.my_project_spring_boot_261021.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.interceptor.AdminAuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns(
                "/members/**",
                "/registration",
                "/registration/**"
            )
            .excludePathPatterns(
                "/members",
                "/members/login",
                "/members/logout-session",
                "/members/forgot-password",
                "/members/reset-password"
            );
        // 後台頁面與 API 使用獨立驗證；只有專用登入 API 不要求既有登入狀態。
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/admin/**", "/admin-events.html", "/admin-members.html", "/admin-reports.html")
            .excludePathPatterns("/admin/auth/login");
    }
}
