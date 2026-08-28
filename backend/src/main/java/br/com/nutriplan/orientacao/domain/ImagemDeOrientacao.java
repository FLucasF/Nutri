package br.com.nutriplan.orientacao.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * A imagem de uma orientação da biblioteca.
 *
 * Tabela própria pelo mesmo motivo do laudo de exame: se o binário ficasse em
 * `orientacao`, toda listagem da biblioteca arrastaria as imagens junto.
 */
@Entity
@Table(name = "imagem_de_orientacao")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ImagemDeOrientacao {

    @Id
    @Column(name = "orientacao_id")
    private Long orientacaoId;

    @Column(nullable = false)
    private byte[] conteudo;

    @CreatedDate
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @LastModifiedDate
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    @CreatedBy
    @Column(name = "criado_por", length = 180, updatable = false)
    private String criadoPor;

    @LastModifiedBy
    @Column(name = "atualizado_por", length = 180)
    private String atualizadoPor;

    public ImagemDeOrientacao(Long orientacaoId, byte[] conteudo) {
        this.orientacaoId = orientacaoId;
        this.conteudo = conteudo;
    }
}
