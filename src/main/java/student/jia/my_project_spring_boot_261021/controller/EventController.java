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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.request.CreateEventRequest;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.response.EventResponse;
import student.jia.my_project_spring_boot_261021.service.EventService;

import org.springframework.web.bind.annotation.PutMapping;
import student.jia.my_project_spring_boot_261021.request.UpdateEventRequest;


import student.jia.my_project_spring_boot_261021.entity.Event;



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


  
    // GET /admin/events
    @GetMapping
    public List<EventResponse> findAll(
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId) {

        // 1. 把登入會員 ID 交給 Service
        // Service 會先檢查管理員身分，再查詢活動
        List<Event> events = eventService.findAll(memberId);

        // 2. 準備裝回應資料的 List
        List<EventResponse> responses = new ArrayList<>();

        // 3. 將每一筆 Event 轉成 EventResponse
         for (Event event : events) {
            responses.add(eventService.toEventResponse(event));
            }

        // 4. 回傳活動列表，Spring 會轉成 JSON 陣列
        return responses;
    }

    // GET /admin/events/1
    // {eventId} 表示這段網址會接收活動 ID
    @GetMapping("/{eventId}")
    public EventResponse findById(
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            @PathVariable("eventId") Long eventId) {

        // 1. memberId：從登入驗證取得，代表誰在操作
        // 2. eventId：從網址取得，代表要查哪一場活動
        Event event = eventService.findById(memberId, eventId);

        // 3. 將查到的活動轉成回應資料
      return eventService.toEventResponse(event);
    }


        // PUT /admin/events/1
    @PutMapping("/{eventId}")
    public ApiResponse update(
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            @PathVariable("eventId") Long eventId,
            @Valid @RequestBody UpdateEventRequest request) {

        eventService.update(memberId, eventId, request);

        return new ApiResponse("修改活動成功");
    }

    // DELETE /admin/events/1：取消整場活動，資料仍保留在資料庫。
    @DeleteMapping("/{eventId}")
    public ApiResponse cancelEvent(
            // 登入者 ID 由驗證攔截器提供，不能由前端自行指定管理員身分。
            @RequestAttribute(
                    name = AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE
            ) Long memberId,
            // eventId 是活動 ID；取消報名才需要查詢個人的報名紀錄。
            @PathVariable("eventId") Long eventId) {

        eventService.cancelEvent(memberId, eventId);

        // Service 正常完成才回傳 HTTP 200；發生例外則交給全域例外處理器。
        return new ApiResponse("活動已取消");
    }

    @RestController
    @RequestMapping("/events")
    public class PublicEventController {

        private final EventService eventService;

        public PublicEventController(EventService eventService) {
            this.eventService = eventService;
        }

        // 玩家查詢活動列表
        @GetMapping
        public List<EventResponse> findAll() {
            List<Event> events = eventService.findPublicEvents();

            List<EventResponse> responses = new ArrayList<>();

            for (Event event : events) {
            responses.add(eventService.toEventResponse(event));
            }

            return responses;
        }

        // 玩家查詢活動詳情
        @GetMapping("/{eventId}")
        public EventResponse findById(
                @PathVariable Long eventId) {

            Event event = eventService.findPublicEventById(eventId);

         return eventService.toEventResponse(event);
        }
    }
}
