package dev.leonardobarros.motor.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Só contas de clientes: a conta interna de liquidação não recebe Pix por chave. */
    @Query("select a from Account a where a.pixKey = :pixKey and a.type = AccountType.CUSTOMER")
    Optional<Account> findByPixKey(@Param("pixKey") String pixKey);

    @Query("select a.id from Account a where a.pixKey = :pixKey and a.type = AccountType.CUSTOMER")
    Optional<UUID> findIdByPixKey(@Param("pixKey") String pixKey);

    /** SELECT ... FOR UPDATE: bloqueia a linha até o fim da transação. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
