package student.jia.my_project_spring_boot_261021.service;

import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import student.jia.my_project_spring_boot_261021.response.RegistrationResponse;
import java.time.LocalDateTime;
import java.util.Optional;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.repository.RegistrationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final MemberRepository memberRepository;
    private final EventRepository eventRepository;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            MemberRepository memberRepository,
            EventRepository eventRepository) {

        this.registrationRepository = registrationRepository;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
    }

    //活動報名
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void register(Long memberId, Long eventId) {
    // 1. 查詢啟用中的會員
    Optional<Member> member =
            memberRepository.findByIdAndStatus(memberId, "ACTIVE");

    if (member.isEmpty()) {
        throw new ResourceNotFoundException(
                "member",
                ResponseMessages.MEMBER_NOT_FOUND);
    }

    // 2. 查詢活動
    Optional<Event> event =
        eventRepository.findByIdForUpdate(eventId);

        if (event.isEmpty()) {
            throw new ResourceNotFoundException(
                    "event",
                    ResponseMessages.EVENT_NOT_FOUND);
        }

        // 從 Optional 裡面取出活動，後面才能讀取活動設定
        Event targetEvent = event.get();

        // 3. 檢查活動是否開放報名
        if (!"OPEN".equals(targetEvent.getStatus())) {
            throw new IllegalArgumentException(
                    ResponseMessages.EVENT_NOT_OPEN);
        }

        LocalDateTime now = LocalDateTime.now();

        // 4. 檢查報名截止時間
        if (!now.isBefore(targetEvent.getRegistrationDeadline())) {
            throw new IllegalArgumentException(
                    ResponseMessages.EVENT_REGISTRATION_DEADLINE_PASSED);
        }

        // 5. 查詢這位會員對這場活動的報名紀錄
        Optional<Registration> existingRegistration =
                registrationRepository.findByMemberIdAndEventId(
                        memberId, eventId);

        if (existingRegistration.isPresent()
                && "REGISTERED".equals(
                        existingRegistration.get().getStatus())) {

            throw new IllegalArgumentException(
                    ResponseMessages.EVENT_REGISTERED_REPEAT);
        }

        // 6. 檢查活動名額
        long registeredCount =
                registrationRepository.countByEventIdAndStatus(
                        eventId, "REGISTERED");

        if (registeredCount >= targetEvent.getCapacity()) {
            throw new IllegalArgumentException(
                    ResponseMessages.EVENT_FULL);
        }

        Registration registration;

        // 7. 判斷是第一次報名，還是取消後再次報名
        if (existingRegistration.isEmpty()) {

            // 第一次報名：建立新物件
            registration = new Registration(memberId, eventId);

        } else {

            // 已取消後再次報名：使用原本那筆紀錄
            registration = existingRegistration.get();

            registration.setStatus("REGISTERED");
            registration.setRegisteredAt(now);
            registration.setCancelledAt(null);
        }

        // 8. 儲存報名紀錄
        registrationRepository.save(registration);
    }

    // 查詢自己對某一場活動的報名紀錄，包含已取消的紀錄
   @Transactional(readOnly = true) //(readOnly = true)則是告訴 Spring：這個方法用途是讀取資料
    public RegistrationResponse findEventById(Long memberId, Long eventId) {

    Optional<Registration> registration =
            registrationRepository.findByMemberIdAndEventId(
                    memberId, eventId);

    if (registration.isEmpty()) {
        throw new ResourceNotFoundException(
                "registration",
                ResponseMessages.REGISTRATION_NOT_FOUND);
    }

    // 報名紀錄已確認存在，接著另外查詢活動，讓查詢與回傳步驟分開。
    Registration targetRegistration = registration.get();
    Optional<Event> optionalEvent = eventRepository.findById(eventId);
    Event event = null;
    if (optionalEvent.isPresent()) {
        event = optionalEvent.get();
    }
    // 維持原本行為：活動不存在時仍回傳報名紀錄，活動相關欄位由回應物件處理。
    return new RegistrationResponse(targetRegistration, event);
    }

   // 查詢自己的全部報名紀錄，包含已取消的紀錄
    @Transactional(readOnly = true)
    public List<RegistrationResponse> findMyRegistrations(Long memberId) {
        List<Registration> registrations = registrationRepository
                .findByMemberIdOrderByRegisteredAtDesc(memberId);
        if (registrations.isEmpty()) {
            return List.of();
        }
        // 1. 收集活動 ID。LinkedHashSet 可去除重複 ID，並保留第一次出現的順序。
        Set<Long> uniqueEventIds = new LinkedHashSet<>();
        for (Registration registration : registrations) {
            uniqueEventIds.add(registration.getEventId());
        }
        List<Long> eventIds = new ArrayList<>(uniqueEventIds);

        // 2. 一次查回所有相關活動，包含已取消的活動，避免每筆報名都查一次資料庫。
        List<Event> relatedEvents = eventRepository.findAllById(eventIds);
        Map<Long, Event> eventsById = new HashMap<>();
        for (Event event : relatedEvents) {
            // 以活動 ID 作為索引，下一步就能找到每筆報名對應的活動。
            eventsById.put(event.getId(), event);
        }

        // 3. 依照原本的報名順序逐筆建立回應；找不到活動時，Map.get 會回傳 null。
        List<RegistrationResponse> responses = new ArrayList<>();
        for (Registration registration : registrations) {
            Event event = eventsById.get(registration.getEventId());
            RegistrationResponse response = new RegistrationResponse(registration, event);
            responses.add(response);
        }
        // 原本 Stream.toList() 的結果不能被增刪，這裡也保留相同限制。
        return Collections.unmodifiableList(responses);
    }

    // 取消自己對某場活動的報名
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long memberId, Long eventId) {

    // 先查詢並鎖定活動，鎖定會維持到交易結束，保留原本避免同時修改的機制。
    Optional<Event> optionalEvent = eventRepository.findByIdForUpdate(eventId);
    if (optionalEvent.isEmpty()) {
        // 查無活動時由 GlobalExceptionHandler.handleResourceNotFound 回傳原本的 404 錯誤。
        throw new ResourceNotFoundException(
                "event", ResponseMessages.EVENT_NOT_FOUND);
    }
    Event targetEvent = optionalEvent.get();

    // 接著保留原本查詢報名紀錄、檢查狀態的程式
        // 1. 查詢自己的報名紀錄
        Optional<Registration> registration =
                registrationRepository.findByMemberIdAndEventId(
                        memberId, eventId);

        if (registration.isEmpty()) {
            throw new ResourceNotFoundException(
                    "registration",
                    ResponseMessages.REGISTRATION_NOT_FOUND);
        }

        Registration targetRegistration = registration.get();

        // 2. 檢查是否已取消
        if ("CANCELLED".equals(targetRegistration.getStatus())) {
            throw new IllegalArgumentException(
                    ResponseMessages.REGISTRATION_ALREADY_CANCELLED);
        }

        LocalDateTime now = LocalDateTime.now();

        // 4. 這裡報名截止前可以自行取消；截止後聯絡管理員處理
      if (!now.isBefore(targetEvent.getRegistrationDeadline())) {
        throw new IllegalArgumentException(
        ResponseMessages.REGISTRATION_CANCELLATION_CLOSED);
}

        // 5. 修改狀態與取消時間
        targetRegistration.setStatus("CANCELLED");
        targetRegistration.setCancelledAt(now);


    
}
 
}

    





