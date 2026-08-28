package br.com.nutriplan.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base das entidades que pertencem a um consultorio.
 *
 * O vinculo e guardado como id cru, e nao como @ManyToOne: a conta e usada
 * apenas como filtro em toda consulta, entao materializar a entidade Conta
 * so geraria join ou lazy-load sem uso. A integridade fica garantida pela
 * foreign key declarada na migration.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class EntidadeDeConta extends BaseEntity {

    @Column(name = "conta_id", nullable = false, updatable = false)
    private Long contaId;

    public boolean pertenceA(Long conta) {
        return contaId != null && contaId.equals(conta);
    }
}
