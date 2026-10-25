package dev.leonardobarros.motor.pix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.account.PixKeyNotFoundException;
import dev.leonardobarros.motor.support.Accounts;
import dev.leonardobarros.motor.transfer.SameAccountTransferException;

@ExtendWith(MockitoExtension.class)
class PixKeyServiceTest {

    @Mock AccountRepository accounts;
    @Mock DictSimulado dict;
    @InjectMocks PixKeyService service;

    @Test
    void showsWhoReceivesWithoutExposingFullData() {
        Account ana = Accounts.active("10.00");
        when(accounts.findByPixKey(ana.getPixKey())).thenReturn(Optional.of(ana));

        PixKeyView view = service.lookup(" " + ana.getPixKey() + " ", UUID.randomUUID());

        assertThat(view.holderName()).isEqualTo(ana.getHolderName());
        assertThat(view.document()).startsWith("***.").endsWith("-**");
        assertThat(view.accountNumber()).startsWith("•••").hasSize(7);
        assertThat(view.acceptsTransfers()).isTrue();
    }

    @Test
    void blockedAccountIsShownAsNotAcceptingTransfers() {
        Account blocked = Accounts.blocked("10.00");
        when(accounts.findByPixKey(blocked.getPixKey())).thenReturn(Optional.of(blocked));

        assertThat(service.lookup(blocked.getPixKey(), UUID.randomUUID()).acceptsTransfers()).isFalse();
    }

    @Test
    void ownKeyIsRefused() {
        Account me = Accounts.active("10.00");
        when(accounts.findByPixKey(me.getPixKey())).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> service.lookup(me.getPixKey(), me.getId()))
                .isInstanceOf(SameAccountTransferException.class);
    }

    @Test
    void unknownKey() {
        when(accounts.findByPixKey("nada@motor.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.lookup("nada@motor.dev", UUID.randomUUID()))
                .isInstanceOf(PixKeyNotFoundException.class);
    }

    @Test
    void masksTheAccountNumber() {
        assertThat(PixKeyService.maskAccountNumber("10002-5")).isEqualTo("•••02-5");
    }
}
