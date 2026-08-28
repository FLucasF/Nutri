package br.com.nutriplan.exame.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * Um parametro laboratorial: glicemia, ferritina, TSH.
 *
 * Conta nula identifica o catalogo do sistema, comum a todos os consultorios;
 * preenchida, parametro proprio — o mesmo eixo do acervo de alimentos.
 */
@Entity
@Table(name = "parametro_exame",
        indexes = @Index(name = "ix_parametro_conta", columnList = "conta_id"))
@Getter
@Setter
@NoArgsConstructor
public class ParametroExame extends BaseEntity {

    @Column(name = "conta_id")
    private Long contaId;

    @Column(nullable = false, length = 120)
    private String nome;

    /**
     * Unidade em que o catalogo espera o valor. Serve para comparar series:
     * valor em outra unidade nao entra na mesma linha do tempo.
     */
    @Column(name = "unidade_padrao", nullable = false, length = 20)
    private String unidadePadrao;

    @Column(length = 60)
    private String grupo;

    @Column(nullable = false)
    private boolean ativo = true;

    @OneToMany(mappedBy = "parametro", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<FaixaDeReferencia> faixas = new ArrayList<>();

    public ParametroExame(Long contaId, String nome, String unidadePadrao, String grupo) {
        this.contaId = contaId;
        this.nome = nome;
        this.unidadePadrao = unidadePadrao;
        this.grupo = grupo;
    }

    public boolean ehDoCatalogoDoSistema() {
        return contaId == null;
    }
}
