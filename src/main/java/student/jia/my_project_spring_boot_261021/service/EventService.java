package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.request.CreateEventRequest;
import org.springframework.transaction.annotation.Transactional;
import student.jia.my_project_spring_boot_261021.entity.Registration;

import student.jia.my_project_spring_boot_261021.repository.RegistrationRepository;
import student.jia.my_project_spring_boot_261021.request.UpdateEventRequest;
import student.jia.my_project_spring_boot_261021.response.EventResponse;
import student.jia.my_project_spring_boot_261021.response.EventPageResponse;

import org.springframework.transaction.annotation.Isolation;
@Service
public class EventService {

    private static final int ADMIN_PAGE_SIZE = 10;

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final RegistrationRepository registrationRepository;

    public EventService(
            EventRepository eventRepository,
            MemberRepository memberRepository,
            RegistrationRepository registrationRepository) {

        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.registrationRepository = registrationRepository;
    }

    //管理員新增活動
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

            updatePresentation(event, request.getCoverTheme(), request.getTags());
            return eventRepository.save(event);
        

        
    }

          // 管理員查詢全部活動
        @Transactional(readOnly = true)
        public List<Event> findAll(Long memberId) {

            // 1. 檢查登入者是否為啟用中的管理員
            validateAdmin(memberId);

            // 2. 查詢所有活動，包含已關閉及已取消的活動
            // 沒有活動時會回傳空 List，不需要丟出例外
            return eventRepository.findAll();
        }


        @Transactional(readOnly = true)
        public EventPageResponse findAdminPage(Long memberId, int page) {
            validateAdmin(memberId);
            if (page < 1) {
                throw new IllegalArgumentException("頁碼必須從 1 開始");
            }

            if ((page - 1L) * ADMIN_PAGE_SIZE > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("頁碼超出查詢範圍");
            }

            // 建立時間相同時，以唯一的活動 ID 決定先後。
            PageRequest pageRequest =
                    PageRequest.of(page - 1, ADMIN_PAGE_SIZE, Sort.Direction.DESC, "createdAt", "id");
            Page<Event> eventPage = eventRepository.findAll(pageRequest);
            List<EventResponse> responses = new ArrayList<>();
            for (Event event : eventPage) {
                responses.add(toEventResponse(event));
            }
            return new EventPageResponse(responses, page, eventPage.getTotalPages(), eventPage.getTotalElements());
        }

        // 管理員查詢單一活動詳情
        @Transactional(readOnly = true)
        public Event findById(Long memberId, Long eventId) {

            // 1. 檢查管理員身分
            validateAdmin(memberId);

            // 2. 根據活動 ID 查詢資料
            Optional<Event> event =
                    eventRepository.findById(eventId);

            // 3. 指定的活動不存在，就回傳找不到活動的錯誤
            if (event.isEmpty()) {
                throw new ResourceNotFoundException(
                        "event",
                        ResponseMessages.EVENT_NOT_FOUND);
            }

            // 4. 從 Optional 取出活動並回傳
            return event.get();
        }


     @Transactional
        // 共用的管理員身分檢查
    // private：目前只讓 EventService 內部的方法呼叫
    private void validateAdmin(Long memberId) {

        // 1. 查詢登入者是否為啟用中的會員
        Optional<Member> member =
                memberRepository.findByIdAndStatus(memberId, "ACTIVE");

        if (member.isEmpty()) {
            throw new ResourceNotFoundException(
                    "member",
                    ResponseMessages.MEMBER_NOT_FOUND);
        }

        // 2. 從 Optional 取出會員物件
        Member operator = member.get();

        // 3. 確認角色是管理員
        if (!"ADMIN".equals(operator.getRole())) {
            throw new IllegalArgumentException("只有管理員可以操作");
        }

    }


        // 管理員修改活動
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(
            Long memberId,
            Long eventId,
            UpdateEventRequest request) {

        // 1. 檢查操作的人是否為啟用中的管理員
        validateAdmin(memberId);

        // 2. 查詢並鎖定要修改的活動
        Optional<Event> event =
                eventRepository.findByIdForUpdate(eventId);

        if (event.isEmpty()) {
            throw new ResourceNotFoundException(
                    "event",
                    ResponseMessages.EVENT_NOT_FOUND);
        }

        Event targetEvent = event.get();

        // 3. 只有開放中的活動可以修改
        if (!"OPEN".equals(targetEvent.getStatus())) {
            throw new IllegalArgumentException(
                    "只有開放中的活動可以修改");
        }

        // 4. 原本的活動已開始，就不允許修改
        LocalDateTime now = LocalDateTime.now();

        if (!now.isBefore(targetEvent.getStartAt())) {
            throw new IllegalArgumentException(
                    "活動已開始，不能修改");
        }

        // 5. 取得這次送來的新時間
        LocalDateTime start = request.getStartAt();
        LocalDateTime end = request.getEndAt();
        LocalDateTime deadline = request.getRegistrationDeadline();

        // 新的活動開始時間也必須晚於現在
        if (!now.isBefore(start)) {
            throw new IllegalArgumentException(
                    "活動開始時間必須晚於現在");
        }

        if (!deadline.isBefore(start)) {
            throw new IllegalArgumentException(
                    "報名截止時間必須早於活動開始時間");
        }

        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "活動開始時間必須早於結束時間");
        }

        // 6. 統計目前仍有效的報名人數
        long registeredCount =
                registrationRepository.countByEventIdAndStatus(
                        eventId, "REGISTERED");

        // 可以改成剛好等於報名人數，但不能更少
        if (request.getCapacity() < registeredCount) {
            throw new IllegalArgumentException(
                    "人數上限不能小於目前已報名人數");
        }

        // 7. 所有條件通過後，修改原本那筆活動
        targetEvent.setTitle(request.getTitle().trim());
        targetEvent.setDescription(request.getDescription().trim());
        targetEvent.setHostName(request.getHostName().trim());
        targetEvent.setLocation(request.getLocation().trim());
        targetEvent.setStartAt(start);
        targetEvent.setEndAt(end);
        targetEvent.setRegistrationDeadline(deadline);
        targetEvent.setCapacity(request.getCapacity());
        targetEvent.setBeginnerFriendly(request.getBeginnerFriendly());
        updatePresentation(targetEvent, request.getCoverTheme(), request.getTags());

        
    }
    private void updatePresentation(Event event, String coverTheme, List<String> tags) {
        if (coverTheme != null) {
            event.setCoverTheme(coverTheme);
        }
        if (tags != null) {
            LinkedHashSet<String> uniqueTags = new LinkedHashSet<>();
            for (String tag : tags) {
                uniqueTags.add(tag.strip());
            }
            event.setCustomTags(String.join("\n", uniqueTags));
        }
    }

    // 取消活動
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public  void cancelEvent ( Long memberId,Long eventId) {
        // 1. 檢查操作的人是否為啟用中的管理員
        validateAdmin(memberId);

        // 2. 查詢並鎖定要取消的活動
        Optional<Event> event =
                eventRepository.findByIdForUpdate(eventId);

        if (event.isEmpty()) {
            throw new ResourceNotFoundException(
                    "event",
                    ResponseMessages.EVENT_NOT_FOUND);
        }

           Event targetEvent = event.get();

        if ("CANCELLED".equals(targetEvent.getStatus())) {
        throw new IllegalArgumentException("活動已取消，不能重複取消");
        }
        // 3. 只有開放中的活動可以取消
        if (!"OPEN".equals(targetEvent.getStatus())) {
            throw new IllegalArgumentException(
                    "只有開放中的活動可以取消");
        }

        // 4. 活動開始前才能取消；剛好等於開始時間也不能取消。
        LocalDateTime now = LocalDateTime.now();

        if (!now.isBefore(targetEvent.getStartAt())) {
            throw new IllegalArgumentException("活動已開始，不能取消");
        }

        // 5. 查出這場活動仍有效的報名。
        // 已經取消的報名不會被查出，因此不會覆寫原本的取消時間。
        List<Registration> registrations =
                registrationRepository.findByEventIdAndStatus(
                        eventId, "REGISTERED");

        // 6. 取消所有有效報名，並記錄這次取消的時間。
        for (Registration registration : registrations) {
            registration.setStatus("CANCELLED");
            registration.setCancelledAt(now);
        }

        // 7. 取消活動本身，避免活動維持 OPEN 而繼續接受報名。
        targetEvent.setStatus("CANCELLED");

        // 本方法有 @Transactional，且資料是在交易內由 JPA 查出，
        // 修改後會在交易提交時自動更新，不需要另外呼叫 save()。
    }


        // 玩家查詢活動列表
    @Transactional(readOnly = true)
    public List<Event> findPublicEvents() {
        return eventRepository.findByStatusOrderByStartAtAsc("OPEN");
    }

    // 玩家查詢活動詳情
    @Transactional(readOnly = true)
    public Event findPublicEventById(Long eventId) {
        // 只查詢開放中的活動，查詢結果先用 Optional 接住，再確認是否存在。
        Optional<Event> optionalEvent =
                eventRepository.findByIdAndStatus(eventId, "OPEN");
        if (optionalEvent.isEmpty()) {
            // 查無活動時由 GlobalExceptionHandler.handleResourceNotFound 回傳原本的 404 錯誤。
            throw new ResourceNotFoundException(
                    "event", ResponseMessages.EVENT_NOT_FOUND);
        }
        return optionalEvent.get();
    }

        // 查詢有效報名人數，組成活動回應
    @Transactional(readOnly = true)
    public EventResponse toEventResponse(Event event) {

        long registeredCount =
                registrationRepository.countByEventIdAndStatus(
                        event.getId(), "REGISTERED");

        return new EventResponse(event, registeredCount);
    }

}


