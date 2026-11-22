package student.jia.my_project_spring_boot_261021.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;
import student.jia.my_project_spring_boot_261021.service.AdminAuthService;

/** 後台一律驗證專用 Session；會員 Bearer Token 不會被當成後台憑證。 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final AdminAuthService auth;

    public AdminAuthInterceptor(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        response.setHeader("Cache-Control", "no-store");
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        boolean page = path.equals("/admin-events.html") || path.equals("/admin-members.html") || path.equals("/admin-reports.html");
        String method = request.getMethod();
        boolean write = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
        try {
            AdminAuthService.AdminIdentity admin = auth.require(request, write);
            // 延用既有 Controller 的操作者屬性；來源改為已驗證的後台 Session。
            request.setAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE, admin.id());
            return true;
        } catch (ResponseStatusException failure) {
            if (page && failure.getStatusCode().value() == 401) {
                response.sendRedirect("/admin-login.html?returnTo=" + path);
                return false;
            }
            throw failure;
        }
    }
}
