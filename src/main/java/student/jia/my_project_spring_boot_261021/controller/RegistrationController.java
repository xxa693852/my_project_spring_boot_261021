package student.jia.my_project_spring_boot_261021.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.service.RegistrationService;

@RestController
@RequestMapping("/registration")
public class RegistrationController {

    private final RegistrationService registrationService;

    // 建構子注入
    public RegistrationController(
            RegistrationService registrationService) {

        this.registrationService = registrationService;
    }

    // 報名活動：目前先供本機測試
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse register(
            @RequestParam Long memberId,
            @RequestParam Long eventId) {

        registrationService.register(memberId, eventId);

        return new ApiResponse("活動報名成功");
    }
}