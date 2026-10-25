package dev.leonardobarros.motor.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByPixKey(String pixKey);

    @Query("select a.id from Account a where a.pixKey = :pixKey")
    Optional<UUID> findIdByPixKey(@Param("pixKey") String pixKey);

    /** SELECT ... FOR UPDATE: bloqueia a linha até o fim da transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
