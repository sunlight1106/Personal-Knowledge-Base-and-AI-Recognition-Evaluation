package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.domain.UserEntity;
import com.robustvision.platform.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationControllerTest {
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final RuntimeHealthService health = mock(RuntimeHealthService.class);
    private final PersonalWorkflowService workflows = mock(PersonalWorkflowService.class);
    private final UserEntity user = mock(UserEntity.class);
    private final NotificationController controller = new NotificationController(current,
            mock(LiveUpdateService.class), mock(SocialService.class), mock(JdbcTemplate.class), mock(GroupConversationService.class));

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(controller, "runtimeHealth", health);
        ReflectionTestUtils.setField(controller, "workflows", workflows);
        when(current.requireCurrent()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(workflows.get(7L)).thenReturn(new PersonalWorkflowService.Preferences(false, false, false, true, 70, List.of(1), "UTC", 0));
    }

    @SuppressWarnings("unchecked")
    private List<NotificationController.Item> items() {
        return (List<NotificationController.Item>) ((ApiResponse<?>) controller.list()).data();
    }

    @Test void backupWarningIsAnActionWithAWorkingDestination() {
        when(current.isSuperAdmin(user)).thenReturn(true);
        when(health.snapshot()).thenReturn(new RuntimeHealthService.Snapshot(java.time.Instant.now(),0,0,0,0,100,100,0, List.of(new RuntimeHealthService.Alert("backup-warning","danger","备份需要处理","检查备份","/app/research?tab=trash"))));
        assertThat(items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("operation-backup-warning");
            assertThat(item.kind()).isEqualTo(NotificationController.Kind.ACTION);
            assertThat(item.url()).isEqualTo("/app/research?tab=trash");
            assertThat(item.popup()).isFalse();
        });
        when(health.snapshot()).thenReturn(new RuntimeHealthService.Snapshot(java.time.Instant.now(),0,0,0,0,100,100,0,List.of()));
        assertThat(items()).isEmpty();
    }

    @Test void regularUsersDoNotReceivePlatformBackupWarnings() {
        assertThat(items()).isEmpty();
        verifyNoInteractions(health);
    }

    @Test void reviewTasksAreKeptWithoutBecomingUnreadMessages() {
        when(current.hasPermission(user, "note:read")).thenReturn(true);
        when(workflows.due(7L)).thenReturn(List.of(Map.of("id", "synthetic-note")));
        assertThat(items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("note-reviews");
            assertThat(item.kind()).isEqualTo(NotificationController.Kind.ACTION);
            assertThat(item.count()).isEqualTo(1);
        });
    }
}
