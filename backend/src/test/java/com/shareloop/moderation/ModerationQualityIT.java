package com.shareloop.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.chat.service.ChatContactFilter;
import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.support.IntegrationTest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Runs the labelled quality corpus through the production tier-one filters and seeded lexicon. */
class ModerationQualityIT extends IntegrationTest {
    @Autowired
    ChatContactFilter chatFilter;

    @Autowired
    ContentModerationService postFilter;

    @Autowired
    JdbcClient jdbc;

    @Test
    void measureChatAndPostTierOne() throws IOException {
        List<Sample> chats = readCorpus("moderation-quality/chat.psv", 4, "C");
        List<Sample> posts = readCorpus("moderation-quality/posts.psv", 5, "P");
        assertThat(chats).hasSize(100);
        assertThat(posts).hasSize(60);
        assertThat(chats.stream().filter(Sample::violation).count()).isEqualTo(50);
        assertThat(posts.stream().filter(Sample::violation).count()).isEqualTo(30);

        Quality chat = new Quality();
        Quality chatContact = new Quality();
        for (Sample sample : chats) {
            String message = sample.parts()[3];
            chat.add(sample, chatFilter.checkViolation(message, true).isPresent());
            if (!sample.kind().equals("prohibited")) {
                chatContact.add(sample, !chatFilter.detectContactInfo(message).isEmpty());
            }
        }

        long categoryId = jdbc.sql(
                        "SELECT id FROM item_categories WHERE is_active AND NOT is_restricted " + "ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
        Quality postFlagged = new Quality();
        Quality postBlocked = new Quality();
        for (Sample sample : posts) {
            ModerationStatus status = postFilter
                    .checkPost(categoryId, sample.parts()[3], sample.parts()[4])
                    .status();
            postFlagged.add(sample, status != ModerationStatus.PASS);
            postBlocked.add(sample, status == ModerationStatus.BLOCK);
        }

        System.out.println("QUALITY chat_tier1 " + chat);
        System.out.println("QUALITY chat_contact_detector " + chatContact);
        System.out.println("QUALITY post_tier1_flagged " + postFlagged);
        System.out.println("QUALITY post_tier1_blocked " + postBlocked);
        assertThat(chat.count()).isEqualTo(100);
        assertThat(chatContact.count()).isEqualTo(90);
        assertThat(postFlagged.count()).isEqualTo(60);
        assertThat(chat.truePositive).isGreaterThanOrEqualTo(40);
        assertThat(chat.falsePositive).isLessThanOrEqualTo(1);
        assertThat(postFlagged.truePositive).isGreaterThanOrEqualTo(26);
        assertThat(postFlagged.falsePositive).isLessThanOrEqualTo(1);
    }

    private static List<Sample> readCorpus(String path, int columns, String prefix) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines =
                    reader.lines().skip(1).filter(line -> !line.isBlank()).toList();
            Set<String> ids = new HashSet<>();
            return lines.stream()
                    .map(line -> {
                        String[] parts = line.split("\\|", -1);
                        assertThat(parts).hasSize(columns);
                        assertThat(parts[0]).startsWith(prefix);
                        assertThat(ids.add(parts[0])).isTrue();
                        assertThat(parts[1]).isIn("true", "false");
                        assertThat(parts[2]).isNotBlank();
                        for (int index = 3; index < columns; index++) {
                            assertThat(parts[index]).isNotBlank();
                        }
                        return new Sample(parts[0], Boolean.parseBoolean(parts[1]), parts[2], parts);
                    })
                    .toList();
        }
    }

    private record Sample(String id, boolean violation, String kind, String[] parts) {}

    private static final class Quality {
        private int truePositive;
        private int falseNegative;
        private int falsePositive;
        private int trueNegative;
        private final Set<String> misses = new HashSet<>();
        private final Set<String> falseAlarms = new HashSet<>();

        void add(Sample sample, boolean observed) {
            if (sample.violation() && observed) {
                truePositive++;
            } else if (sample.violation()) {
                falseNegative++;
                misses.add(sample.id());
            } else if (observed) {
                falsePositive++;
                falseAlarms.add(sample.id());
            } else {
                trueNegative++;
            }
        }

        int count() {
            return truePositive + falseNegative + falsePositive + trueNegative;
        }

        @Override
        public String toString() {
            return "tp=" + truePositive + " fn=" + falseNegative + " fp=" + falsePositive + " tn=" + trueNegative
                    + " recall=" + percent(truePositive, truePositive + falseNegative)
                    + " falsePositiveRate=" + percent(falsePositive, falsePositive + trueNegative)
                    + " misses=" + misses.stream().sorted().toList()
                    + " falseAlarms=" + falseAlarms.stream().sorted().toList();
        }

        private static String percent(int numerator, int denominator) {
            return String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * numerator / denominator);
        }
    }
}
