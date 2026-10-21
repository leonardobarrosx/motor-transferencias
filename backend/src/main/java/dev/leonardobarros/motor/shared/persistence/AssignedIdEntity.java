package dev.leonardobarros.motor.shared.persistence;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Entidades imutáveis com id gerado no domínio. Sem isso o Spring Data trataria
 * todo save() como merge e faria um SELECT antes de cada INSERT do livro-razão.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        isNew = false;
    }
}
