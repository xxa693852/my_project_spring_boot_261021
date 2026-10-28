package student.jia.my_project_spring_boot_261021.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.request.CreateEventRequest;
import org.springframework.transaction.annotation.Transactional;


@Service
public class EventService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;

    public EventService(
            EventRepository eventRepository,
            MemberRepository memberRepository) {

        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Event create(
            Long memberId,
            CreateEventRequest request) {

        // 1. 查詢啟用中的會員
        Optional<Member> member =
                memberRepository.findByIdAndStatus(memberId, "ACTIVE");

            if (member.isEmpty()) {
                throw new ResourceNotFoundException(
                        "member",
                        ResponseMessages.MEMBER_NOT_FOUND);
            }

        Member operator = member.get();

        // 2. 檢查管理員角色
            if (!"ADMIN".equals(operator.getRole())) {
                throw new IllegalArgumentException("只有管理員可以新增活動");
            }

        // 3. 報名截止時間必須晚於現在 
        LocalDateTime now = LocalDateTime.now(); // 程式執行當下的時間

            if (!now.isBefore(request.getRegistrationDeadline())) {
                throw new IllegalArgumentException(
                        ResponseMessages.EVENT_REGISTRATION_DEADLINE_PASSED);
            }

            LocalDateTime start = request.getStartAt(); // 前端指定的活動開始時間
            LocalDateTime end = request.getEndAt();
            LocalDateTime deadline = request.getRegistrationDeadline();

            // 報名截止時間必須早於活動開始時間
            if (!deadline.isBefore(start)) {
                throw new IllegalArgumentException( "報名截止時間必須早於活動開始時間");
            }
            // 活動開始時間必須早於結束時間
            if (!start.isBefore(end)) {
                    throw new IllegalArgumentException("活動開始時間必須早於結束時間");
            }

            Event event = new Event(
            request.getTitle(),
            request.getDescription(),
            request.getHostName(),
            request.getLocation(),
            start,
            end,
            deadline,
            request.getCapacity(),
            request.getBeginnerFriendly(),
            operator.getId()
            );

            return eventRepository.save(event);
        

        
    }



}



        // 1. 查詢啟用中的會員

        // 2. 檢查角色是否為 ADMIN

        // 3. 檢查報名截止、開始、結束時間的先後順序

        // 4. 建立 Event，放入活動資料與建立者 ID

        // 5. 儲存並回傳活動
    
