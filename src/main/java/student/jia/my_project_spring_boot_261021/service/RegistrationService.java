package student.jia.my_project_spring_boot_261021.service;

import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.entity.Registration;

import java.time.LocalDateTime;
import java.util.Optional;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.repository.RegistrationRepository;
import org.springframework.transaction.annotation.Transactional;
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

@Transactional
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
    Optional<Event> event = eventRepository.findById(eventId);

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



}
   

