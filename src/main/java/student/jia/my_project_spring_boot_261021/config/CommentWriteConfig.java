package student.jia.my_project_spring_boot_261021.config;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.service.CommentWriteLimiter;
/** 身分驗證後、JSON 與 @Valid 前計算嘗試，無效內容也不能無限重送。 */
@Configuration
public class CommentWriteConfig implements WebMvcConfigurer,HandlerInterceptor {
 private final CommentWriteLimiter limiter;
 public CommentWriteConfig(CommentWriteLimiter limiter) { this.limiter=limiter; }
 @Override public void addInterceptors(InterceptorRegistry registry) {
  registry.addInterceptor(this).addPathPatterns("/members/me/events/*/comments","/members/me/events/*/comments/*").order(100);
 }
 @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
  if (!"POST".equals(request.getMethod()) && !"PUT".equals(request.getMethod())) return true;
  Long memberId=(Long)request.getAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE);
  if (memberId==null) return true;
  String rejection=limiter.attempt(memberId);
  if (rejection!=null) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,rejection);
  return true;
 }
}
