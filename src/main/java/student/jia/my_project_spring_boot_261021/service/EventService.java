package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
/**
 * 活動業務流程：管理員維護活動、玩家查詢活動，以及計算名額與回應資料。
 * 修改交易內查出的 Entity 時，JPA 會在交易提交時偵測變更並寫入資料庫。
 */
@Service
public class EventService {

    private static final int ADMIN_PAGE_SIZE = 10;
    private static final int PUBLIC_PAGE_SIZE = 6;

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final RegistrationRepository registrationRepository;
    private final CancellationNotificationQueue notificationQueue;
    private final NewebPayService newebPay;
    private final student.jia.my_project_spring_boot_261021.repository.PaymentRepository payments;

    public EventService(
            EventRepository eventRepository,
            MemberRepository memberRepository,
            RegistrationRepository registrationRepository,
            CancellationNotificationQueue notificationQueue, NewebPayService newebPay,
            student.jia.my_project_spring_boot_261021.repository.PaymentRepository payments) {
        this.newebPay = newebPay;
        this.payments = payments;

        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.registrationRepository = registrationRepository;
        this.notificationQueue = notificationQueue;
    }

    //管理員新增活動
    // 檢查管理員與活動時間，建立活動並回傳儲存後的 Event；Controller 另外回傳成功訊息。
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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei")); // 程式執行當下的時間

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

            // 舊版表單可能省略報名費。Event 的 int 預設為 0，代表免費。
            Integer registrationFee = request.getRegistrationFee();
            if (registrationFee != null) {
                event.setRegistrationFee(registrationFee);
            }
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


        // 回傳管理員活動分頁；先在資料庫篩選，再計算總筆數與取出指定頁。
        @Transactional(readOnly = true)
        public EventPageResponse findAdminPage(Long memberId, int page, String keyword, boolean editableOnly) {
            validateAdmin(memberId);
            PageRequest pageRequest = eventPageRequest(page, ADMIN_PAGE_SIZE,
                    Sort.Direction.DESC, "createdAt");
            // 先套用搜尋條件再分頁，總筆數才會涵蓋所有符合條件的活動。
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
            Page<Event> matches = eventRepository.searchAdmin(eventKeywordPattern(keyword), editableOnly, now, pageRequest);
            return toEventPageResponse(matches, page);
        }

    // 先確認管理員與活動存在，再回傳該活動取消通知的投遞紀錄。
    @Transactional(readOnly = true)
    public List<CancellationNotificationQueue.Delivery> findCancellationNotifications(Long memberId, Long eventId) {
        findById(memberId, eventId);
        return notificationQueue.findByEvent(eventId);
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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        //isBefore 是用來判斷某個日期或時間是否早於另一個指定時間的方法，通常回傳布林值（true 或 false）
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
                        eventId, "REGISTERED"); //REGISTERED 已註冊

        // 可以改成剛好等於報名人數，但不能更少
        if (request.getCapacity() < registeredCount + registrationRepository.countActiveHolds(eventId, now)) {
            throw new IllegalArgumentException(
                    "人數上限不能小於目前已報名人數");
        }

        // 7. 所有條件通過後，修改原本那筆活動
        // 保留期間固定費用與時段，讓已簽發的付款表單仍對應同一個條件。
        if (registrationRepository.countActiveHolds(eventId, now) > 0
                && (!start.equals(targetEvent.getStartAt()) || !end.equals(targetEvent.getEndAt())
                || !deadline.equals(targetEvent.getRegistrationDeadline())
                || (request.getRegistrationFee() != null
                && request.getRegistrationFee() != targetEvent.getRegistrationFee()))) {
            throw new IllegalArgumentException("仍有待付款報名，暫時無法修改費用或時段。");
        }
        if (registeredCount > 0 && request.getRegistrationFee() != null
                && request.getRegistrationFee() != targetEvent.getRegistrationFee()) {
            throw new IllegalArgumentException("已有確認報名，無法修改報名費。");
        }
        targetEvent.setTitle(request.getTitle().trim()); //trim())用來去除字串頭尾多餘空白字元的函式或方法
        targetEvent.setDescription(request.getDescription().trim()); //Description 活動介紹
        targetEvent.setHostName(request.getHostName().trim()); // 主持人
        targetEvent.setLocation(request.getLocation().trim()); //地點
        targetEvent.setStartAt(start); //開始時間
        targetEvent.setEndAt(end);
        targetEvent.setRegistrationDeadline(deadline);//報名截止時間
        targetEvent.setCapacity(request.getCapacity()); //報名人數名額上限
        if (request.getRegistrationFee() != null) { //報名費
            targetEvent.setRegistrationFee(request.getRegistrationFee());
        }
        targetEvent.setBeginnerFriendly(request.getBeginnerFriendly());
        updatePresentation(targetEvent, request.getCoverTheme(), request.getTags());

        
    }   // 下方方法共用封面主題與標籤的更新邏輯。
    // 新增與修改活動共用：儲存封面主題識別值與標籤，不處理圖片上傳。
    // null 表示保留原設定；空標籤清單會清除標籤，非空清單會去除頭尾空白與重複值。
    private void updatePresentation(Event event, String coverTheme, List<String> tags) {
       
        if (coverTheme != null) {
            event.setCoverTheme(coverTheme); //若有傳入封面選項，就把它存進 Event
        }
        if (tags != null) {
            LinkedHashSet<String> uniqueTags = new LinkedHashSet<>(); //LinkedHashSet 會去除重複值，並保留加入的順序
            for (String tag : tags) {
                uniqueTags.add(tag.strip());
            }
            event.setCustomTags(String.join("\n", uniqueTags)); // 更新活動封面主題與自訂標籤，標籤去除頭尾空白及重複值
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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));

        if (!now.isBefore(targetEvent.getStartAt())) {
            throw new IllegalArgumentException("活動已開始，不能取消");
        }

        // 5. 查出這場活動仍有效的報名。
        // 已經取消的報名不會被查出，因此不會覆寫原本的取消時間。
        List<Registration> registrations =
                registrationRepository.findByEventIdAndStatus(
                        eventId, "REGISTERED");

        // 6. 取消所有有效報名，並記錄這次取消的時間。
        registrations = new ArrayList<>(registrations);
        registrations.addAll(registrationRepository.findByEventIdAndStatus(eventId, "PENDING_PAYMENT"));
        for (Registration registration : registrations) {
            boolean notify = !"PENDING_PAYMENT".equals(registration.getStatus())
                    || now.isBefore(registration.getPaymentExpiresAt());
            registration.setStatus("CANCELLED");
            registration.setCancelledAt(now);
            registration.setPaymentExpiresAt(null);
            String paymentNote = PaymentLifecycle.cancelLatest(payments, registration.getId());
            if (notify) {
                notificationQueue.enqueue(targetEvent, registration.getMemberId(), paymentNote);
            }
        }

        // 7. 取消活動本身，避免活動維持 OPEN 而繼續接受報名。
        targetEvent.setStatus("CANCELLED");

        // 本方法有 @Transactional，且資料是在交易內由 JPA 查出，
        // 修改後會在交易提交時自動更新，不需要另外呼叫 save()。
    }


        // 玩家查詢活動列表
    // 依關鍵字、日期、新手與可報名條件回傳公開分頁；查詢截止日包含當日整天。
    @Transactional(readOnly = true)
    public EventPageResponse findPublicPage(int page, String keyword, LocalDate from, LocalDate through,
            boolean beginnerOnly, boolean availableOnly) {
        if (from != null && through != null && from.isAfter(through)) {
            throw new IllegalArgumentException("起始日期不可晚於截止日期。");
        }
        if ((from != null && (from.getYear() < 1000 || from.getYear() > 9999))
                || (through != null && (through.getYear() < 1000 || through.getYear() > 9999))) {
            throw new IllegalArgumentException("活動日期超出查詢範圍。");
        }

        PageRequest pageRequest = eventPageRequest(page, PUBLIC_PAGE_SIZE, Sort.Direction.ASC, "startAt");
        String pattern = eventKeywordPattern(keyword);
        LocalDateTime startFrom = null;
        if (from != null) {
            startFrom = from.atStartOfDay();
        }
        LocalDateTime until = null;
        if (through != null && !through.equals(LocalDate.of(9999, 12, 31))) {
            until = through.plusDays(1).atStartOfDay();
        }
        Page<Event> matches = eventRepository.searchPublic(pattern, startFrom, until,
                beginnerOnly, availableOnly, newebPay.isReady(), LocalDateTime.now(ZoneId.of("Asia/Taipei")), pageRequest);
        return toEventPageResponse(matches, page);
    }

    // 將關鍵字轉成 LIKE 搜尋樣式；空白回傳 null，特殊字元跳脫後以一般文字搜尋。
    private String eventKeywordPattern(String keyword) {
        String pattern = null;
        if (keyword != null && !keyword.isBlank()) {
            String term = keyword.strip();
            if (term.length() > 100) {
                throw new IllegalArgumentException("搜尋關鍵字最多 100 字。");
            }
            // 將 LIKE 的特殊字元當成搜尋文字。
            pattern = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        return pattern;
    }

    // 檢查頁碼並建立分頁條件；畫面從第 1 頁開始，JPA 的索引則從 0 開始。
    private PageRequest eventPageRequest(int page, int pageSize, Sort.Direction direction, String sortField) {
        if (page < 1) {
            throw new IllegalArgumentException("頁碼必須從 1 開始");
        }
        if ((page - 1L) * pageSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("頁碼超出查詢範圍");
        }

        // 時間相同時，以唯一的活動 ID 決定順序。
        return PageRequest.of(page - 1, pageSize, direction, sortField, "id");
    }

    // 將當頁 Event 逐筆轉為 EventResponse，並保留總頁數與總筆數。
    private EventPageResponse toEventPageResponse(Page<Event> eventPage, int page) {
        List<EventResponse> responses = new ArrayList<>();
        for (Event event : eventPage) {
            responses.add(toEventResponse(event));
        }
        return new EventPageResponse(responses, page, eventPage.getTotalPages(), eventPage.getTotalElements());
    }

    // 回傳開放、未從列表隱藏且尚未結束的活動，依開始時間及 ID 排序。
    @Transactional(readOnly = true)
    public List<Event> findPublicEvents() {
        return eventRepository.findByStatusAndHiddenFromPublicListFalseAndEndAtAfterOrderByStartAtAscIdAsc(
                "OPEN", LocalDateTime.now(ZoneId.of("Asia/Taipei")));
    }

    // 管理員調整活動在公開列表的顯示；不會改變活動狀態或直接取消報名。
    @Transactional
    public void changeListVisibility(Long memberId, Long eventId, boolean hidden) {
        validateAdmin(memberId);
        Optional<Event> result = eventRepository.findByIdForUpdate(eventId);
        if (result.isEmpty()) {
            throw new ResourceNotFoundException("event", ResponseMessages.EVENT_NOT_FOUND);
        }
        Event event = result.get();
        if (!"OPEN".equals(event.getStatus())
                || !LocalDateTime.now(ZoneId.of("Asia/Taipei")).isBefore(event.getEndAt())) {
            throw new IllegalArgumentException("只有尚未結束的開放活動可以調整公開列表顯示。");
        }
        event.setHiddenFromPublicList(hidden);
    }

    // 玩家查詢活動詳情
    // 依 ID 回傳可公開查詢狀態的活動；詳情查詢不以列表隱藏欄位篩選。
    @Transactional(readOnly = true)
    public Event findPublicEventById(Long eventId) {
        Event event = eventRepository.findById(eventId).orElse(null);
        if (event == null || !isPublicEvent(event)) {
            throw new ResourceNotFoundException("event", ResponseMessages.EVENT_NOT_FOUND);
        }
        return event;
    }

    // 只判斷活動狀態是否允許公開查看詳情，不代表目前仍可報名。
    static boolean isPublicEvent(Event event) {
        return "OPEN".equals(event.getStatus()) || "CLOSED".equals(event.getStatus())
                || "CANCELLED".equals(event.getStatus());
    }

        // 查詢有效報名人數，組成活動回應
    // 統計已確認報名與有效待付款保留，回傳含名額資訊的 EventResponse。
    @Transactional(readOnly = true)
    public EventResponse toEventResponse(Event event) {

        long registeredCount =
                registrationRepository.countByEventIdAndStatus(
                        event.getId(), "REGISTERED");

        long activeHolds = registrationRepository.countActiveHolds(event.getId(), PaymentLifecycle.now());
        return new EventResponse(event, registeredCount, activeHolds, newebPay.isReady());
    }

}


