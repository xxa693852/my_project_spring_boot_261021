package student.jia.my_project_spring_boot_261021.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.response.RegistrationResponse;
import student.jia.my_project_spring_boot_261021.service.RegistrationService;

@RestController
@RequestMapping("/registration")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {

        this.registrationService = registrationService;
    }

    // 報名活動
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse register(
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            @RequestParam Long eventId) {

        registrationService.register(memberId, eventId);

        return new ApiResponse("活動報名成功");
    }

    // 查詢自己對某一場活動的報名紀錄
    @GetMapping("/events/{eventId}")
    public RegistrationResponse findOne(
           @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            @PathVariable Long eventId) {

        Registration registration =
                registrationService.findEventById(memberId, eventId);

        return new RegistrationResponse(registration);
    }

    // 查詢自己的全部報名紀錄
    @GetMapping
    public List<RegistrationResponse> findAll(
        @RequestAttribute(
            name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
        ) Long memberId) {

        List<Registration> registrations =
                registrationService.findMyRegistrations(memberId);

        List<RegistrationResponse> responses = new ArrayList<>();

        for (Registration registration : registrations) {
            responses.add(new RegistrationResponse(registration));
    }

    return responses;
}
    // 取消報名
    @DeleteMapping("/events/{eventId}")
    public ApiResponse cancel(
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            @PathVariable Long eventId) {

        registrationService.cancel(memberId, eventId);

        return new ApiResponse("活動報名已取消");
    }
}