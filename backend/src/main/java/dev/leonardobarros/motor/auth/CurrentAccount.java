package dev.leonardobarros.motor.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injeta no controller o id da conta de quem fez a requisição, lido do JWT.
 * A conta nunca vem do corpo ou da URL: assim ninguém opera a conta de outra
 * pessoa trocando um id (IDOR).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentAccount {
}
