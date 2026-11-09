package com.shareloop.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.notification.event.RealtimeUpdateEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class RealtimeServiceTest {
    @Mock
    DataSource dataSource;

    @Mock
    SseEmitter receiver;

    @Mock
    SseEmitter otherUser;

    @Mock
    SseEmitter catalogViewer;

    @Mock
    Connection connection;

    @Mock
    PreparedStatement statement;

    @Test
    void committedUpdatePublishesScopedPayload() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        new RealtimeService(dataSource).publishCommitted(new RealtimeUpdateEvent(12L, "REQUEST", 101L));

        verify(statement).setString(1, "shareloop_realtime");
        verify(statement).setString(2, "U:12:REQUEST:101");
        verify(statement).execute();
    }

    @Test
    @SuppressWarnings("unchecked")
    void privateUpdateOnlyReachesMatchingUser() throws Exception {
        RealtimeService service = new RealtimeService(dataSource);
        var streams = (ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>)
                ReflectionTestUtils.getField(service, "userStreams");
        streams.put(12L, new CopyOnWriteArrayList<>(java.util.List.of(receiver)));
        streams.put(13L, new CopyOnWriteArrayList<>(java.util.List.of(otherUser)));

        ReflectionTestUtils.invokeMethod(service, "dispatch", "U:12:REQUEST:101");

        verify(receiver).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUser, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicCatalogUpdateReachesGuestsAndSignedInUsers() throws Exception {
        RealtimeService service = new RealtimeService(dataSource);
        var streams = (ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>)
                ReflectionTestUtils.getField(service, "userStreams");
        streams.put(12L, new CopyOnWriteArrayList<>(java.util.List.of(receiver)));
        var catalogs = (CopyOnWriteArrayList<SseEmitter>) ReflectionTestUtils.getField(service, "catalogStreams");
        catalogs.add(catalogViewer);

        ReflectionTestUtils.invokeMethod(service, "dispatch", "C:0:CATALOG:42");

        verify(catalogViewer).send(any(SseEmitter.SseEventBuilder.class));
        verify(receiver).send(any(SseEmitter.SseEventBuilder.class));
    }
}
