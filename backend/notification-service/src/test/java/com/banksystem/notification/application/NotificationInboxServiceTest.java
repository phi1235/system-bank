package com.banksystem.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banksystem.common.exception.BusinessException;
import com.banksystem.notification.domain.NotificationLogEntity;
import com.banksystem.notification.domain.NotificationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class NotificationInboxServiceTest {

  private NotificationLogRepository repository;
  private NotificationInboxService service;
  private final UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private final UUID otherUser = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

  @BeforeEach
  void setUp() {
    repository = mock(NotificationLogRepository.class);
    service = new NotificationInboxService(repository);
  }

  @Test
  void myInbox_mapsReadFlag() {
    NotificationLogEntity unread = row(userId, null);
    NotificationLogEntity read = row(userId, Instant.parse("2026-07-21T10:00:00Z"));
    when(repository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(unread, read)));

    var page = service.myInbox(userId, 0, 20);
    assertEquals(2, page.items().size());
    assertFalse(page.items().get(0).read());
    assertTrue(page.items().get(1).read());
  }

  @Test
  void markRead_setsTimestamp() {
    NotificationLogEntity unread = row(userId, null);
    when(repository.findByIdAndUserId(unread.getId(), userId)).thenReturn(Optional.of(unread));
    when(repository.save(any(NotificationLogEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var item = service.markRead(userId, unread.getId());
    assertTrue(item.read());
    verify(repository).save(any(NotificationLogEntity.class));
  }

  @Test
  void markRead_wrongOwnerNotFound() {
    when(repository.findByIdAndUserId(any(), eq(userId))).thenReturn(Optional.empty());
    BusinessException ex = assertThrows(
        BusinessException.class,
        () -> service.markRead(userId, UUID.randomUUID()));
    assertEquals("NOTIFICATION_NOT_FOUND", ex.getCode());
  }

  @Test
  void unreadCount_delegates() {
    when(repository.countByUserIdAndReadAtIsNull(userId)).thenReturn(3L);
    assertEquals(3L, service.unreadCount(userId));
  }

  @Test
  void markAllRead_returnsUpdated() {
    when(repository.markAllRead(userId)).thenReturn(5);
    assertEquals(5, service.markAllRead(userId));
  }

  private NotificationLogEntity row(UUID owner, Instant readAt) {
    NotificationLogEntity e = new NotificationLogEntity();
    e.setId(UUID.randomUUID());
    e.setEventId(UUID.randomUUID());
    e.setChannel("EMAIL");
    e.setRecipient("alice@bank.local");
    e.setTemplate("TRANSFER_COMPLETED");
    e.setStatus("SENT");
    e.setBody("ok");
    e.setUserId(owner);
    e.setReadAt(readAt);
    e.setCreatedAt(Instant.parse("2026-07-20T08:00:00Z"));
    return e;
  }
}
