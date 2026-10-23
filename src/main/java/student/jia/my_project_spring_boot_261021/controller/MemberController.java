package student.jia.my_project_spring_boot_261021.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import jakarta.validation.Valid;
import student.jia.my_project_spring_boot_261021.request.MemberRegisterRequest;
import student.jia.my_project_spring_boot_261021.service.MemberService;




@RestController //這個 Class 是一個 Web API 控制器；告訴 Spring 這個類別專門用來接收 HTTP 請求（如 GET、POST），並且直接把執行的結果「轉換成資料（如 JSON 或 XML）」傳回給前端，而不是去渲染 HTML 網頁
@RequestMapping("/members")
public class MemberController {
    
    public  final MemberService memberService;

    //建構子注入
    public MemberController(MemberService memberService){
        this.memberService = memberService;
    }

    //會員註冊
    @PostMapping
    @ResponseStatus (HttpStatus.CREATED)
    public ApiResponse register(
            @Valid @RequestBody MemberRegisterRequest request) {
                memberService.register(request);
                return new ApiResponse("會員註冊成功");
    }


}
