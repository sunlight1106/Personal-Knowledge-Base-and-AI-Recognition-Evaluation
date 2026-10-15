package com.robustvision.platform.service;

import com.robustvision.platform.domain.UserEntity;
import com.robustvision.platform.domain.UserSessionEntity;
import com.robustvision.platform.repository.UserSessionRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserSessionEfficiencyTest {
    @Test void freshSessionAvoidsWritesButRevocationIsCheckedEveryTime() {
        var repo = mock(UserSessionRepository.class);var user = mock(UserEntity.class);
        when(user.hasActiveAccess(any())).thenReturn(true);
        var session = new UserSessionEntity(user, Instant.now(), Instant.now().plusSeconds(3600), null);
        when(repo.findActive(eq("session"), eq("user"), any())).thenReturn(Optional.of(session));
        var service = new UserSessionService(repo);
        for (int i=0;i<20;i++) assertThat(service.isActive("session", "user")).isTrue();
        verify(repo, times(20)).findActive(eq("session"), eq("user"), any());
        verify(repo, never()).touch(any(), any(), any());
        when(repo.findActive(eq("session"), eq("user"), any())).thenReturn(Optional.empty());
        assertThat(service.isActive("session", "user")).isFalse();
    }
    @Test void staleActivityUpdatesAndDisabledAccountDoesNotAuthenticate() {
        var repo = mock(UserSessionRepository.class);var user = mock(UserEntity.class);
        when(user.hasActiveAccess(any())).thenReturn(true);
        var session = new UserSessionEntity(user, Instant.now().minusSeconds(600), Instant.now().plusSeconds(3600), null);
        when(repo.findActive(any(), any(), any())).thenReturn(Optional.of(session));
        var service = new UserSessionService(repo);
        assertThat(service.isActive("s", "u")).isTrue();verify(repo).touch(eq("s"), any(), any());
        when(user.hasActiveAccess(any())).thenReturn(false);
        assertThat(service.isActive("s", "u")).isFalse();
        verify(repo, times(1)).touch(any(), any(), any());
    }
}
