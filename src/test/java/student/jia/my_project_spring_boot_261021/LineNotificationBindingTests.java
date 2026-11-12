package student.jia.my_project_spring_boot_261021;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import student.jia.my_project_spring_boot_261021.controller.LineNotificationController;
import student.jia.my_project_spring_boot_261021.controller.LineNotificationController.LinkCallback;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.service.CancellationNotificationQueue;
import student.jia.my_project_spring_boot_261021.service.LineNotificationClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LineNotificationBindingTests {
    @Test
    void callbackRequiresOriginalBrowserAndMemberAndCanOnlyBeUsedOnce() {
        MemberRepository members = mock(MemberRepository.class);
        LineNotificationClient line = mock(LineNotificationClient.class);
        CancellationNotificationQueue queue = mock(CancellationNotificationQueue.class);
        LineNotificationController controller = new LineNotificationController(members, line, queue);
        Member member = new Member("qa", "hash", "QA", "qa@example.com");
        when(members.findByIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(member));
        when(members.findByIdAndStatus(2L, "ACTIVE")).thenReturn(Optional.of(member));
        when(line.isConfigured()).thenReturn(true);
        when(queue.isEnabled()).thenReturn(true);

        MockHttpSession browser = new MockHttpSession();
        controller.start(1L, browser);
        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nonce = ArgumentCaptor.forClass(String.class);
        verify(line).authorizationUrl(state.capture(), nonce.capture());
        LinkCallback callback = new LinkCallback("code", state.getValue());

        assertThrows(IllegalArgumentException.class,
                () -> controller.complete(1L, new MockHttpSession(), callback));
        assertThrows(IllegalArgumentException.class,
                () -> controller.complete(2L, browser, callback));
        verify(line, never()).verifyAndGetFriend(anyString(), anyString());

        when(line.verifyAndGetFriend("code", nonce.getValue())).thenReturn("verified-user");
        when(members.updateNotificationLineUserId(1L, "verified-user")).thenReturn(1);
        assertTrue(controller.complete(1L, browser, callback).linked());
        assertThrows(IllegalArgumentException.class, () -> controller.complete(1L, browser, callback));
        verify(line).verifyAndGetFriend("code", nonce.getValue());
        verify(members).updateNotificationLineUserId(1L, "verified-user");
    }
}
