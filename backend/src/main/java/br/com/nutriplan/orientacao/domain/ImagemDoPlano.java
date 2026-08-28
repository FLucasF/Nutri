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
 * A cópia da imagem entregue num plano.
 *
 * É cópia, e não referência à imagem da biblioteca, pela mesma razão do texto:
 * trocar a figura no modelo depois mudaria o que dezenas de pacientes já
 * receberam. O custo são algumas centenas de KB por plano; a alternativa é um
 * plano entregue que muda sozinho.
 */
@Entity
@Table(name = "imagem_do_plano")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ImagemDoPlano {

    @Id
    @Column(name = "orientacao_do_plano_id")
    private Long orientacaoDoPlanoId;

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

    public ImagemDoPlano(Long orientacaoDoPlanoId, byte[] conteudo) {
        this.orientacaoDoPlanoId = orientacaoDoPlanoId;
        this.conteudo = conteudo;
    }
}
