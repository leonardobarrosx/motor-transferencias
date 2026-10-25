package dev.leonardobarros.motor.pix;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.account.AccountView;
import dev.leonardobarros.motor.account.PixKeyNotFoundException;
import dev.leonardobarros.motor.transfer.SameAccountTransferException;

@Service
public class PixKeyService {

    public static final String INSTITUTION = "Motor de Transferências";

    private final AccountRepository accounts;
    private final DictSimulado dict;

    PixKeyService(AccountRepository accounts, DictSimulado dict) {
        this.accounts = accounts;
        this.dict = dict;
    }

    @Transactional(readOnly = true)
    public PixKeyView lookup(String pixKey, UUID requesterAccountId) {
        var internal = accounts.findByPixKey(pixKey.strip());
        if (internal.isEmpty()) {
            // não é cliente daqui: o DICT diz de quem é e em qual instituição
            return dict.consultar(pixKey.strip())
                    .map(external -> new PixKeyView(external.pixKey(), external.holderName(),
                            AccountView.mask(external.document()), external.institution(), external.branch(),
                            maskAccountNumber(external.accountNumber()), true))
                    .orElseThrow(PixKeyNotFoundException::new);
        }
        Account account = internal.get();
        if (account.getId().equals(requesterAccountId)) {
            throw new SameAccountTransferException();
        }
        return new PixKeyView(
                account.getPixKey(),
                account.getHolderName(),
                AccountView.mask(account.getDocument()),
                INSTITUTION,
                account.getBranch(),
                maskAccountNumber(account.getNumber()),
                account.isActive());
    }

    /** "10002-5" → "•••02-5" */
    static String maskAccountNumber(String number) {
        int keep = Math.min(4, number.length());
        return "•••" + number.substring(number.length() - keep);
    }
}
