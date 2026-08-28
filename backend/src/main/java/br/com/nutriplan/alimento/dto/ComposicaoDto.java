package br.com.nutriplan.alimento.dto;

import br.com.nutriplan.alimento.domain.ComposicaoNutricional;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composicao nutricional exposta pela API.
 *
 * Serializa como um objeto plano — {"energiaKcal": 123.5, "proteinaG": 2.59} —
 * e nao como um registro de campos fixos. Assim incluir um nutriente novo no
 * catalogo aparece na API sem alterar este arquivo, e nutrientes nao
 * determinados na fonte simplesmente nao aparecem no JSON.
 *
 * Chave ausente significa nutriente nao determinado. O cliente deve exibir
 * "não informado", nunca zero: sao coisas diferentes num contexto clinico.
 */
public record ComposicaoDto(@JsonValue Map<String, BigDecimal> valores) {

    public ComposicaoDto {
        valores = valores == null ? Map.of() : Map.copyOf(valores);
    }

    @JsonCreator
    public static ComposicaoDto doJson(Map<String, BigDecimal> valores) {
        return new ComposicaoDto(valores == null ? Map.of() : new LinkedHashMap<>(valores));
    }

    public static ComposicaoDto de(ComposicaoNutricional composicao) {
        return composicao == null ? null : new ComposicaoDto(composicao.comoMapa());
    }

    /**
     * Chaves desconhecidas sao ignoradas em silencio: um cliente antigo ou uma
     * planilha importada podem trazer colunas que o catalogo nao reconhece, e
     * isso nao deve derrubar o cadastro do alimento.
     */
    public ComposicaoNutricional paraDominio() {
        var composicao = new ComposicaoNutricional();
        valores.forEach(composicao::definir);
        return composicao;
    }

    public BigDecimal valorDe(String chave) {
        return valores.get(chave);
    }

    public boolean vazia() {
        return valores.isEmpty();
    }
}
