package student.jia.my_project_spring_boot_261021.controller;


import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.request.CreateEventRequest;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.response.RegistrationResponse;
import student.jia.my_project_spring_boot_261021.service.EventService;
import student.jia.my_project_spring_boot_261021.service.RegistrationService;

@RestController
@RequestMapping("/admin/events")
public class EventController {
    
    private  final EventService eventService;

    public  EventController(EventService  eventService){
        this.eventService = eventService;
    }



    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse create(
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            @Valid @RequestBody CreateEventRequest request) {

        eventService.create(memberId, request);

        return new ApiResponse("建立活動成功");
}
}
