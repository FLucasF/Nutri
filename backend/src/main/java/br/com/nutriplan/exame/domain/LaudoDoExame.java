package br.com.nutriplan.exame.domain;

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
 * O arquivo do laudo, numa tabela so dele.
 *
 * Separado de {@link Exame} de proposito: se o binario ficasse na mesma tabela,
 * toda listagem de resultados arrastaria os arquivos junto. Aqui ele so e lido
 * quando alguem pede o download.
 *
 * Nao estende BaseEntity porque a chave e o proprio id do exame — a relacao e
 * um para um, e uma chave sintetica so acrescentaria uma coluna sem uso.
 */
@Entity
@Table(name = "laudo_exame")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class LaudoDoExame {

    @Id
    @Column(name = "exame_id")
    private Long exameId;

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

    public LaudoDoExame(Long exameId, byte[] conteudo) {
        this.exameId = exameId;
        this.conteudo = conteudo;
    }
}
