package student.jia.my_project_spring_boot_261021.service;

import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import java.util.List;
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
    public Registration findEventById(Long memberId, Long eventId) {

    Optional<Registration> registration =
            registrationRepository.findByMemberIdAndEventId(
                    memberId, eventId);

    if (registration.isEmpty()) {
        throw new ResourceNotFoundException(
                "registration",
                ResponseMessages.REGISTRATION_NOT_FOUND);
    }

    return registration.get();
    }

   // 查詢自己的全部報名紀錄，包含已取消的紀錄
    @Transactional(readOnly = true)
    public List<Registration> findMyRegistrations(Long memberId) {

        return registrationRepository
            .findByMemberIdOrderByRegisteredAtDesc(memberId);
    }

    // 取消自己對某場活動的報名
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long memberId, Long eventId) {

    Event targetEvent = eventRepository
        .findByIdForUpdate(eventId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "event", ResponseMessages.EVENT_NOT_FOUND));

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

    





