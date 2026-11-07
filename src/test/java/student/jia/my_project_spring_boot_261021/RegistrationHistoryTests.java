package student.jia.my_project_spring_boot_261021;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.repository.RegistrationRepository;
import student.jia.my_project_spring_boot_261021.response.RegistrationResponse;
import student.jia.my_project_spring_boot_261021.service.RegistrationService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationHistoryTests {
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final RegistrationService service = new RegistrationService(
            registrations, mock(MemberRepository.class), events);

    private Event event(long id, String status, LocalDateTime deadline) {
        Event event = new Event("History event " + id, "Description", "Host", "Taipei",
                deadline.plusDays(1), deadline.plusDays(2), deadline, 3, true, 1L);
        event.setId(id);
        event.setStatus(status);
        return event;
    }

    @Test
    void historyIncludesCancelledEventsAndPreservesRegistrationOrder() {
        Event open = event(11L, "OPEN", LocalDateTime.now().plusDays(1));
        Event cancelled = event(12L, "CANCELLED", LocalDateTime.now().plusDays(1));
        Registration first = new Registration(7L, 12L);
        first.setStatus("CANCELLED");
        Registration second = new Registration(7L, 11L);
        when(registrations.findByMemberIdOrderByRegisteredAtDesc(7L))
                .thenReturn(List.of(first, second));
        when(events.findAllById(List.of(12L, 11L))).thenReturn(List.of(open, cancelled));

        List<RegistrationResponse> result = service.findMyRegistrations(7L);

        assertEquals(2, result.size());
        assertEquals(12L, result.get(0).getEventId());
        assertEquals("History event 12", result.get(0).getEventTitle());
        assertEquals("CANCELLED", result.get(0).getEventStatus());
        assertEquals(cancelled.getStartAt(), result.get(0).getStartAt());
        assertEquals("Taipei", result.get(0).getLocation());
        assertFalse(result.get(0).isCanCancel());
        assertTrue(result.get(1).isCanCancel());
        verify(events).findAllById(List.of(12L, 11L));
        verifyNoMoreInteractions(events);
    }

    @Test
    void emptyHistoryDoesNotQueryEvents() {
        when(registrations.findByMemberIdOrderByRegisteredAtDesc(8L)).thenReturn(List.of());
        assertTrue(service.findMyRegistrations(8L).isEmpty());
        verifyNoInteractions(events);
    }

    @Test
    void cancellationEligibilityRespectsDeadlineAndRegistrationStatus() {
        Registration registration = new Registration(7L, 11L);
        Event event = event(11L, "OPEN", LocalDateTime.now().plusDays(1));
        assertTrue(new RegistrationResponse(registration, event).isCanCancel());
        event.setRegistrationDeadline(LocalDateTime.now().minusSeconds(1));
        assertFalse(new RegistrationResponse(registration, event).isCanCancel());
        event.setRegistrationDeadline(LocalDateTime.now().plusDays(1));
        registration.setStatus("CANCELLED");
        assertFalse(new RegistrationResponse(registration, event).isCanCancel());
    }

    @Test
    void missingEventDoesNotBreakTheRestOfHistory() {
        Registration registration = new Registration(7L, 99L);
        when(registrations.findByMemberIdOrderByRegisteredAtDesc(7L)).thenReturn(List.of(registration));
        when(events.findAllById(List.of(99L))).thenReturn(List.of());
        RegistrationResponse result = service.findMyRegistrations(7L).getFirst();
        assertEquals(99L, result.getEventId());
        assertNull(result.getEventTitle());
        assertFalse(result.isCanCancel());
    }

    @Test
    void repeatedEventIdsAreQueriedOnceWithoutDroppingRegistrations() {
        // 活動 ID 去重只影響批次查詢，不應減少報名紀錄的筆數或改變順序。
        Event relatedEvent = event(11L, "OPEN", LocalDateTime.now().plusDays(1));
        Registration first = new Registration(7L, 11L);
        Registration second = new Registration(7L, 11L);
        second.setStatus("CANCELLED");
        when(registrations.findByMemberIdOrderByRegisteredAtDesc(7L))
                .thenReturn(List.of(first, second));
        when(events.findAllById(List.of(11L))).thenReturn(List.of(relatedEvent));

        List<RegistrationResponse> result = service.findMyRegistrations(7L);

        assertEquals(2, result.size());
        assertEquals("REGISTERED", result.get(0).getStatus());
        assertEquals("CANCELLED", result.get(1).getStatus());
        verify(events).findAllById(List.of(11L));
        verifyNoMoreInteractions(events);
    }

    @Test
    void singleRegistrationSurvivesMissingEvent() {
        // 查無活動仍應保留報名紀錄，不因 Optional 寫法調整而新增例外。
        Registration registration = new Registration(7L, 99L);
        when(registrations.findByMemberIdAndEventId(7L, 99L))
                .thenReturn(Optional.of(registration));
        when(events.findById(99L)).thenReturn(Optional.empty());

        RegistrationResponse result = service.findEventById(7L, 99L);

        assertEquals(99L, result.getEventId());
        assertEquals("REGISTERED", result.getStatus());
        assertNull(result.getEventTitle());
        assertFalse(result.isCanCancel());
    }
}
