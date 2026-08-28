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
 * Orientacao como um paciente a recebeu, presa a um plano.
 *
 * Guarda o texto, e nao um ponteiro para a biblioteca. A copia e o ponto: se o
 * plano apenas apontasse, corrigir um modelo mudaria em silencio o que dezenas
 * de pacientes ja receberam — inclusive plano encerrado, que e registro do que
 * foi prescrito. E a mesma regra do peso gravado no item de refeicao.
 *
 * A copia tambem e o que permite personalizar: o nutricionista adapta o texto
 * para aquele paciente sem sujar o modelo.
 */
@Entity
@Table(name = "orientacao_do_plano",
        indexes = @Index(name = "ix_orientacao_plano", columnList = "plano_id"))
@Getter
@Setter
@NoArgsConstructor
public class OrientacaoDoPlano extends BaseEntity {

    @Column(name = "plano_id", nullable = false)
    private Long planoId;

    /** Procedencia, nao dependencia: pode ficar nulo sem afetar o texto. */
    @Column(name = "orientacao_id")
    private Long orientacaoId;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(nullable = false, length = 8000)
    private String corpo;

    /** Nome e tipo aqui; o binario em imagem_do_plano. */
    @Column(name = "imagem_nome", length = 200)
    private String imagemNome;

    @Column(name = "imagem_tipo", length = 100)
    private String imagemTipo;

    public boolean temImagem() {
        return imagemNome != null;
    }

    @Column(nullable = false)
    private Integer ordem;

    public OrientacaoDoPlano(Long planoId, String titulo, String corpo, int ordem) {
        this.planoId = planoId;
        this.titulo = titulo;
        this.corpo = corpo;
        this.ordem = ordem;
    }
}
