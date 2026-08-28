package br.com.nutriplan.shared.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Base de todas as entidades persistentes: identidade, auditoria e
 * equals/hashCode seguros para entidades JPA (baseados no id, tolerando
 * instancias ainda nao persistidas e proxies do Hibernate).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @CreatedBy
    @Column(name = "criado_por", updatable = false)
    private String criadoPor;

    @LastModifiedBy
    @Column(name = "atualizado_por")
    private String atualizadoPor;

    public boolean isNovo() {
        return id == null;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> esta = org.hibernate.Hibernate.getClass(this);
        Class<?> outra = org.hibernate.Hibernate.getClass(o);
        if (!esta.equals(outra)) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public final int hashCode() {
        return org.hibernate.Hibernate.getClass(this).hashCode();
    }
}
