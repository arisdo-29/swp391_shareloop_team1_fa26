package com.shareloop.notification.service;

import com.shareloop.notification.event.RealtimeUpdateEvent;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE theo người dùng và thay đổi bài công khai; PostgreSQL NOTIFY chuyển tín hiệu giữa các instance. */
@Service
public class RealtimeService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeService.class);
    private static final String CHANNEL = "shareloop_realtime";
    private static final long STREAM_TIMEOUT_MS = 240_000;

    private final DataSource dataSource;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> userStreams = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> catalogStreams = new CopyOnWriteArrayList<>();
    private final AtomicBoolean listening = new AtomicBoolean();

    public RealtimeService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SseEmitter subscribeUser(long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        userStreams
                .computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        registerCleanup(emitter, () -> removeUserStream(userId, emitter));
        startListener();
        send(emitter, new RealtimeUpdateEvent(userId, "READY", null));
        return emitter;
    }

    public SseEmitter subscribeCatalog() {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        catalogStreams.add(emitter);
        registerCleanup(emitter, () -> catalogStreams.remove(emitter));
        startListener();
        send(emitter, new RealtimeUpdateEvent(null, "READY", null));
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishCommitted(RealtimeUpdateEvent event) {
        String payload = (event.userId() == null ? "C" : "U") + ":"
                + (event.userId() == null ? 0 : event.userId()) + ":"
                + event.kind() + ":"
                + (event.resourceId() == null ? 0 : event.resourceId());
        try {
            // Lấy kết nối riêng: AFTER_COMMIT vẫn còn tài nguyên transaction gắn với thread hiện tại.
            try (Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement("SELECT pg_notify(?, ?)")) {
                statement.setString(1, CHANNEL);
                statement.setString(2, payload);
                statement.execute();
            }
        } catch (Exception ex) {
            // Giao dịch nghiệp vụ đã commit; lỗi luồng trực tiếp không được làm hỏng phản hồi API.
            log.warn("Could not publish realtime update", ex);
        }
    }

    private void startListener() {
        if (!listening.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("shareloop-realtime-listener").start(() -> {
            try {
                while (hasSubscribers()) {
                    try (Connection connection = dataSource.getConnection();
                            Statement statement = connection.createStatement()) {
                        statement.execute("LISTEN " + CHANNEL);
                        PGConnection pgConnection = connection.unwrap(PGConnection.class);
                        // Đọc lại sau khi LISTEN thực sự hoạt động để bù sự kiện tới lúc vừa kết nối.
                        userStreams.forEach((userId, streams) -> streams.forEach(
                                emitter -> send(emitter, new RealtimeUpdateEvent(userId, "READY", null))));
                        catalogStreams.forEach(emitter -> send(emitter, new RealtimeUpdateEvent(null, "READY", null)));
                        while (hasSubscribers()) {
                            PGNotification[] notifications = pgConnection.getNotifications(5_000);
                            if (notifications == null) continue;
                            for (PGNotification notification : notifications) dispatch(notification.getParameter());
                        }
                    } catch (Exception ex) {
                        log.warn("Realtime listener reconnecting", ex);
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            } finally {
                listening.set(false);
                if (hasSubscribers()) startListener();
            }
        });
    }

    private boolean hasSubscribers() {
        return !catalogStreams.isEmpty() || userStreams.values().stream().anyMatch(streams -> !streams.isEmpty());
    }

    private void dispatch(String payload) {
        String[] parts = payload.split(":", 4);
        if (parts.length != 4) return;
        try {
            long userId = Long.parseLong(parts[1]);
            long resourceId = Long.parseLong(parts[3]);
            if (!parts[2].matches("[A-Z_]+")) return;
            RealtimeUpdateEvent event = new RealtimeUpdateEvent(
                    "C".equals(parts[0]) ? null : userId, parts[2], resourceId == 0 ? null : resourceId);
            if ("C".equals(parts[0])) {
                catalogStreams.forEach(emitter -> send(emitter, event));
                userStreams.values().forEach(streams -> streams.forEach(emitter -> send(emitter, event)));
            } else if ("U".equals(parts[0])) {
                userStreams.getOrDefault(userId, new CopyOnWriteArrayList<>()).forEach(emitter -> send(emitter, event));
            }
        } catch (NumberFormatException ignored) {
            // Bỏ qua payload không hợp lệ.
        }
    }

    private void send(SseEmitter emitter, RealtimeUpdateEvent event) {
        try {
            emitter.send(SseEmitter.event().name("update").data(event));
        } catch (IOException | IllegalStateException ex) {
            emitter.complete();
        }
    }

    private void registerCleanup(SseEmitter emitter, Runnable cleanup) {
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(ignored -> cleanup.run());
    }

    private void removeUserStream(long userId, SseEmitter emitter) {
        userStreams.computeIfPresent(userId, (ignored, streams) -> {
            streams.remove(emitter);
            return streams.isEmpty() ? null : streams;
        });
    }
}
