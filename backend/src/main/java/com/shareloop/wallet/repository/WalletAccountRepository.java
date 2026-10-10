package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.WalletAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {

    /**
     * Đọc ví và khoá dòng users đến hết transaction (SELECT ... FOR UPDATE). Hai giao dịch cùng ví phải chờ nhau,
     * nên không thể cùng đọc "còn đủ tiền" rồi cùng trừ. Chỉ CreditService gọi hàm này, trong transaction của nó.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletAccount w where w.id = :id")
    Optional<WalletAccount> lockById(@Param("id") Long id);
}
