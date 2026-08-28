package br.com.nutriplan.orientacao.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Texto de orientacao na biblioteca do consultorio.
 *
 * Existe para o nutricionista nao reescrever "como montar o prato" a cada
 * paciente. O acervo segue o mesmo eixo dos alimentos e das medidas caseiras:
 * conta nula identifica modelo do sistema, comum a todos; conta preenchida,
 * texto do consultorio, visivel so para ele.
 */
@Entity
@Table(name = "orientacao", indexes = @Index(name = "ix_orientacao_conta", columnList = "conta_id"))
@Getter
@Setter
@NoArgsConstructor
public class Orientacao extends BaseEntity {

    @Column(name = "conta_id")
    private Long contaId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 8000)
    private String corpo;

    /** Nome e tipo aqui; o binario em imagem_de_orientacao. */
    @Column(name = "imagem_nome", length = 200)
    private String imagemNome;

    @Column(name = "imagem_tipo", length = 100)
    private String imagemTipo;

    public boolean temImagem() {
        return imagemNome != null;
    }

    @Column(nullable = false)
    private boolean ativo = true;

    public Orientacao(Long contaId, String titulo, String corpo) {
        this.contaId = contaId;
        this.titulo = titulo;
        this.corpo = corpo;
    }

    /** Modelo do sistema: serve de ponto de partida, e ninguem o edita. */
    public boolean ehModeloDoSistema() {
        return contaId == null;
    }
}
