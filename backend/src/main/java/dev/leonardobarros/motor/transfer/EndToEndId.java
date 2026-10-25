package dev.leonardobarros.motor.transfer;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Identificador fim a fim no formato do Pix (32 caracteres):
 * {@code E + ISPB(8) + yyyyMMddHHmm (UTC) + 11 alfanuméricos}; devoluções usam {@code D}.
 */
public final class EndToEndId {

    /** ISPB fictício: o simulador não representa nenhuma instituição real. */
    public static final String SIMULATOR_ISPB = "99999999";

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private EndToEndId() {
    }

    public static String generate(Instant now) {
        return generate('E', now);
    }

    /** {@code D…}: o identificador de uma devolução (o Pix real usa a mesma convenção). */
    public static String generateReturn(Instant now) {
        return generate('D', now);
    }

    private static String generate(char prefix, Instant now) {
        StringBuilder id = new StringBuilder(32)
                .append(prefix)
                .append(SIMULATOR_ISPB)
                .append(TIMESTAMP.format(now));
        for (int i = 0; i < 11; i++) {
            id.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return id.toString();
    }
}
