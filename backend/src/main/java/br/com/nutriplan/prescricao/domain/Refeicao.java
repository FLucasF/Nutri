package br.com.nutriplan.prescricao.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Uma refeicao do plano — café da manhã, almoço, ceia.
 *
 * A ordem e explicita e nao derivada do horario: o profissional pode querer o
 * pre-treino logo apos o almoco na lista, ainda que o relogio diga outra coisa,
 * e ha planos sem horario definido.
 */
@Entity
@Table(name = "refeicao", indexes = @Index(name = "ix_refeicao_plano", columnList = "plano_id"))
@Getter
@Setter
@NoArgsConstructor
public class Refeicao extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plano_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_refeicao_plano"))
    private PlanoAlimentar plano;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column
    private LocalTime horario;

    @Column(nullable = false)
    private Integer ordem;

    /** Orientacao especifica da refeicao, exibida ao paciente. */
    @Column(length = 1000)
    private String observacao;

    // Carregado em lote: sem isso, ler um plano de seis refeicoes dispararia
    // seis consultas de itens.
    @OneToMany(mappedBy = "refeicao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem asc")
    @BatchSize(size = 50)
    private List<ItemRefeicao> itens = new ArrayList<>();

    public Refeicao(String nome, LocalTime horario) {
        this.nome = nome;
        this.horario = horario;
    }

    public void adicionarItem(ItemRefeicao item) {
        item.setRefeicao(this);
        if (item.getOrdem() == null) {
            item.setOrdem(proximaOrdem());
        }
        itens.add(item);
    }

    public void removerItem(ItemRefeicao item) {
        itens.remove(item);
        item.setRefeicao(null);
    }

    private int proximaOrdem() {
        return itens.stream()
                .map(ItemRefeicao::getOrdem)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0) + 1;
    }

    public void renumerarItens() {
        List<ItemRefeicao> ordenados = new ArrayList<>(itens);
        ordenados.sort(Comparator.comparing(
                ItemRefeicao::getOrdem, Comparator.nullsLast(Comparator.naturalOrder())));
        for (int i = 0; i < ordenados.size(); i++) {
            ordenados.get(i).setOrdem(i + 1);
        }
    }
}
