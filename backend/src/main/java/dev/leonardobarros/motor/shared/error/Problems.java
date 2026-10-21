package dev.leonardobarros.motor.shared.error;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Fábrica única de Problem Details, para que todo erro tenha o mesmo formato. */
public final class Problems {

    private static final String TYPE_PREFIX = "https://motor.dev/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        return problem;
    }
}
