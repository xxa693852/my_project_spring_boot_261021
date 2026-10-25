package student.jia.my_project_spring_boot_261021.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.response.MemberResponse;
import student.jia.my_project_spring_boot_261021.response.TokenResponse;
import jakarta.validation.Valid;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;
import student.jia.my_project_spring_boot_261021.request.MemberPasswordUpdateRequest;
import student.jia.my_project_spring_boot_261021.request.MemberRegisterRequest;
import student.jia.my_project_spring_boot_261021.request.MemberUpdateRequest;
import student.jia.my_project_spring_boot_261021.request.TokenLogoutRequest;
import student.jia.my_project_spring_boot_261021.service.MemberService;




@RestController //這個 Class 是一個 Web API 控制器；告訴 Spring 這個類別專門用來接收 HTTP 請求（如 GET、POST），並且直接把執行的結果「轉換成資料（如 JSON 或 XML）」傳回給前端，而不是去渲染 HTML 網頁
@RequestMapping("/members")
public class MemberController {
    
    public  final MemberService memberService;

    //建構子注入
    public MemberController(MemberService memberService){
        this.memberService = memberService;
    }
    //單一會員ID查詢
    @GetMapping ("/{id}")
    @ResponseStatus (HttpStatus.OK) // 指定此 API 成功回應的 HTTP 狀態碼為 200 OK
    // @PathVariable : 從 URL 路徑擷取目標會員 ID
    public  MemberResponse getOneById(@PathVariable Long id){
        return new MemberResponse(memberService.findOneById(id));
    }


    //會員註冊
    @PostMapping
    @ResponseStatus (HttpStatus.CREATED)
    public ApiResponse register(
            @Valid @RequestBody MemberRegisterRequest request) {
                memberService.register(request);
                return new ApiResponse("會員註冊成功");
    }

    @PutMapping ("/{id}/profile")
    @ResponseStatus (HttpStatus.OK)
    public ApiResponse updateProfile(
            @PathVariable Long id,
            @Valid  @RequestBody MemberUpdateRequest request){
                memberService.updateProfile(id, request);
                return  new ApiResponse("會員基本資料修改成功");

            }
    @PutMapping ("/{id}/password")
    @ResponseStatus (HttpStatus.OK)
    public  ApiResponse updatePassword(
            @PathVariable  Long id,
            @Valid  @RequestBody MemberPasswordUpdateRequest request){
                memberService.updatePassword(id, request);
                return new ApiResponse("會員密碼修改成功");
            }
    //會員登入
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse login(@Valid @RequestBody MemberLoginRequest request) {
        return memberService.login(request);
    }
    //會員登出
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse logout(
        @Valid @RequestBody TokenLogoutRequest request) {

        memberService.logout(request.getRefreshToken());
        return new ApiResponse("登出成功");
    }
    //會員停用
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse disabled(@PathVariable Long id) {
        memberService.disableMember(id);
        return new ApiResponse("會員帳號停用成功");
    }
    

}
