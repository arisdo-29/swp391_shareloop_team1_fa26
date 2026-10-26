package com.shareloop.user.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.item.PostLimitProperties;
import com.shareloop.user.entity.User;
import com.shareloop.user.entity.UserStatus;
import com.shareloop.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class TestAccountSeederTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    PostLimitProperties postLimitProperties;

    @BeforeEach
    void setUp() {
        postLimitProperties = new PostLimitProperties();
        postLimitProperties.setPostLimitBypassEmails(List.of("tester1@shareloop.test", "Tester2@shareloop.test"));
    }

    private TestAccountSeeder seederWithPassword(String password) {
        return new TestAccountSeeder(userRepository, passwordEncoder, postLimitProperties, password);
    }

    @Test
    void seeder_whenEnabledIsFalse_isNotRegisteredAsBean() {
        // given
        ConditionalOnProperty condition = TestAccountSeeder.class.getAnnotation(ConditionalOnProperty.class);

        // when
        String havingValue = condition.havingValue();

        // then: không có property hoặc property khác "true" thì Spring không tạo bean, nên không chạy gì
        assertThat(condition.name()).containsExactly("shareloop.seed-test-accounts.enabled");
        assertThat(havingValue).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
    }

    @Test
    void run_whenAccountsNotExist_createsVerifiedAccounts() {
        // given
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        // when
        seederWithPassword("secret").run(null);

        // then
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        User first = captor.getAllValues().get(0);
        User second = captor.getAllValues().get(1);
        assertThat(first.getEmail()).isEqualTo("tester1@shareloop.test");
        assertThat(second.getEmail()).isEqualTo("tester2@shareloop.test");
        assertThat(first.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(first.getPasswordHash()).isEqualTo("hashed");
        assertThat(first.isAdmin()).isFalse();
        assertThat(first.getPhone()).isNotEqualTo(second.getPhone());
    }

    @Test
    void run_whenEmailAlreadyExists_skipsThatAccount() {
        // given
        when(userRepository.findByEmail("tester1@shareloop.test"))
                .thenReturn(Optional.of(
                        new User("tester1@shareloop.test", "old", "Cũ", "0900000000", false, UserStatus.ACTIVE)));
        when(userRepository.findByEmail("tester2@shareloop.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("hashed");

        // when
        seederWithPassword("secret").run(null);

        // then: chỉ tạo tài khoản chưa có, không ghi đè tài khoản cũ
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("tester2@shareloop.test");
    }

    @Test
    void run_whenPasswordMissing_createsNothing() {
        // given
        TestAccountSeeder seeder = seederWithPassword("");

        // when
        seeder.run(null);

        // then
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }
}
