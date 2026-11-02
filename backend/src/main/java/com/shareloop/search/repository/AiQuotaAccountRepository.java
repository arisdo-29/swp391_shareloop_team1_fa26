package com.shareloop.search.repository;

import com.shareloop.search.entity.AiQuotaAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AiQuotaAccountRepository extends JpaRepository<AiQuotaAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AiQuotaAccount account where account.id = :userId")
    Optional<AiQuotaAccount> lockByUserId(long userId);
}
