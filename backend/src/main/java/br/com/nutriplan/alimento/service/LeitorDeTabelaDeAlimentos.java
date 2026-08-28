package br.com.nutriplan.alimento.service;

import br.com.nutriplan.alimento.domain.Alimento;
import br.com.nutriplan.alimento.domain.ComposicaoNutricional;
import br.com.nutriplan.alimento.domain.FonteDeDados;
import br.com.nutriplan.alimento.domain.MedidaCaseira;
import br.com.nutriplan.alimento.domain.Nutriente;
import br.com.nutriplan.shared.util.LeitorCsv;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Le uma tabela de alimentos em CSV e devolve entidades prontas para gravar.
 *
 * Existe para que o sistema nao fique amarrado a uma fonte especifica: a mesma
 * rotina importa a TACO, um recorte do Open Food Facts ou uma planilha que o
 * nutricionista montou, bastando informar a fonte. As colunas de nutriente sao
 * casadas contra o catalogo em {@link Nutriente}, entao acrescentar um nutriente
 * ao catalogo o torna importavel sem tocar aqui.
 *
 * O casamento de colunas e tolerante: ignora acentos, maiusculas e os
 * separadores usuais, de modo que "Energia (kcal)", "energia_kcal" e
 * "energiaKcal" chegam todos ao mesmo campo.
 */
@Component
@Slf4j
public class LeitorDeTabelaDeAlimentos {

    /** Nomes alternativos aceitos para as colunas descritivas. */
    private static final Map<String, List<String>> ALIAS_DESCRITIVOS = Map.of(
            "descricao",     List.of("descricao", "descricaodoalimento", "descricaodosalimentos",
                                     "nome", "alimento", "produto", "productname"),
            "codigofonte",   List.of("codigofonte", "codigo", "id", "codigotaco", "codigoibge"),
            "codigobarras",  List.of("codigobarras", "ean", "gtin", "barcode", "code"),
            "marca",         List.of("marca", "marcas", "brand", "brands", "fabricante"),
            "grupo",         List.of("grupo", "categoria", "categorias", "grupodealimentos", "category"),
            "quantidade",    List.of("quantidade", "quantity", "embalagem")
    );

    /** Resultado da leitura, com os avisos acumulados para exibir a quem importou. */
    public record Resultado(List<Alimento> alimentos, List<String> avisos, int linhasIgnoradas) {
        public boolean vazio() {
            return alimentos.isEmpty();
        }
    }

    public Resultado ler(Reader entrada, FonteDeDados fonte, char separador) throws IOException {
        List<Alimento> alimentos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int ignoradas = 0;
        int semNutrientes = 0;

        try (var reader = new BufferedReader(entrada)) {
            String cabecalho = reader.readLine();
            if (cabecalho == null) {
                avisos.add("O arquivo esta vazio.");
                return new Resultado(alimentos, avisos, 0);
            }

            Map<String, Integer> colunas = mapearColunas(LeitorCsv.dividir(cabecalho, separador));
            Map<String, Integer> nutrientes = mapearNutrientes(LeitorCsv.dividir(cabecalho, separador));

            if (!colunas.containsKey("descricao")) {
                avisos.add("Não encontrei a coluna de descrição do alimento. "
                        + "Nomeie a coluna como \"descricao\", \"nome\" ou \"alimento\".");
                return new Resultado(alimentos, avisos, 0);
            }
            if (nutrientes.isEmpty()) {
                avisos.add("Nenhuma coluna de nutriente reconhecida. "
                        + "Use nomes como \"energiaKcal\", \"proteinaG\" ou \"Energia (kcal)\".");
                return new Resultado(alimentos, avisos, 0);
            }

            log.debug("Colunas reconhecidas: descritivas={}, nutrientes={}",
                    colunas.keySet(), nutrientes.keySet());

            String linha;
            int numero = 1;
            while ((linha = reader.readLine()) != null) {
                numero++;
                if (linha.isBlank()) {
                    continue;
                }
                String[] campos = LeitorCsv.dividir(linha, separador);
                Alimento alimento = montar(campos, colunas, nutrientes, fonte);
                if (alimento == null) {
                    ignoradas++;
                    if (avisos.size() < 20) {
                        avisos.add("Linha %d ignorada: sem descrição do alimento.".formatted(numero));
                    }
                    continue;
                }
                if (alimento.getComposicao().vazia()) {
                    semNutrientes++;
                }
                alimentos.add(alimento);
            }
        }

        if (ignoradas > 20) {
            avisos.add("... e mais %d linhas ignoradas pelo mesmo motivo.".formatted(ignoradas - 20));
        }
        if (semNutrientes > 0) {
            avisos.add(("%d alimentos entraram sem nenhum nutriente preenchido e aparecerao como "
                    + "\"nao informado\".").formatted(semNutrientes));
        }
        return new Resultado(alimentos, avisos, ignoradas);
    }

    private Alimento montar(String[] campos, Map<String, Integer> colunas,
                            Map<String, Integer> nutrientes, FonteDeDados fonte) {

        String descricao = valor(campos, colunas.get("descricao"));
        if (descricao == null) {
            return null;
        }

        var composicao = new ComposicaoNutricional();
        nutrientes.forEach((chave, indice) -> {
            BigDecimal valor = numero(valor(campos, indice));
            if (valor != null) {
                composicao.definir(chave, valor);
            }
        });
        // Composicao vazia nao invalida a linha: tabelas oficiais trazem itens
        // cujos nutrientes nao foram determinados (a TACO tem um), e sumir com
        // eles em silencio falsearia a base. Entram no acervo exibindo
        // "não informado", e quem importou e avisado de quantos foram.
        var alimento = new Alimento(recortar(descricao, 250), fonte);
        alimento.setComposicao(composicao);
        alimento.setCodigoFonte(recortar(valor(campos, colunas.get("codigofonte")), 30));
        alimento.setCodigoBarras(recortar(valor(campos, colunas.get("codigobarras")), 20));
        alimento.setMarca(recortar(valor(campos, colunas.get("marca")), 100));
        alimento.setGrupo(recortar(valor(campos, colunas.get("grupo")), 100));

        // Produtos industrializados costumam trazer so o codigo de barras; usa-lo
        // tambem como codigo da fonte mantem a chave de deduplicacao preenchida.
        if (alimento.getCodigoFonte() == null && alimento.getCodigoBarras() != null) {
            alimento.setCodigoFonte(alimento.getCodigoBarras());
        }

        porcaoDaEmbalagem(valor(campos, colunas.get("quantidade")))
                .ifPresent(alimento::adicionarMedida);

        return alimento;
    }

    private static final java.util.regex.Pattern QUANTIDADE = java.util.regex.Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(kg|g|gr|gramas?|ml|l|lt|litros?)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Deriva a porcao "1 embalagem" a partir do peso declarado no rotulo.
     *
     * E a porcao que faz sentido para um industrializado: o paciente entende
     * "1 lata" ou "1 pacote", nao "395 g".
     *
     * Mililitro e tratado como grama de proposito. Rotulos de liquidos declaram
     * a composicao por 100 ml, e e nessa mesma base que ela foi importada — ler
     * 1 L como 1000 g mantem o calculo coerente com o dado de origem, sem
     * precisar da densidade de cada produto.
     */
    private java.util.Optional<MedidaCaseira> porcaoDaEmbalagem(String quantidade) {
        if (quantidade == null) {
            return java.util.Optional.empty();
        }
        var m = QUANTIDADE.matcher(quantidade);
        if (!m.find()) {
            return java.util.Optional.empty();
        }
        BigDecimal valor = numero(m.group(1));
        if (valor == null || valor.signum() <= 0) {
            return java.util.Optional.empty();
        }
        String unidade = m.group(2).toLowerCase();
        BigDecimal gramas = switch (unidade) {
            case "kg", "l", "lt", "litro", "litros" -> valor.multiply(BigDecimal.valueOf(1000));
            default -> valor;
        };
        // Embalagens absurdas (erro de digitacao) nao viram porcao.
        if (gramas.compareTo(BigDecimal.valueOf(20000)) > 0) {
            return java.util.Optional.empty();
        }

        var medida = new MedidaCaseira("embalagem (%s)".formatted(quantidade.trim()),
                gramas.stripTrailingZeros());
        medida.setPadrao(true);
        return java.util.Optional.of(medida);
    }

    private Map<String, Integer> mapearColunas(String[] cabecalho) {
        Map<String, Integer> encontradas = new HashMap<>();
        for (int i = 0; i < cabecalho.length; i++) {
            String normalizada = normalizar(cabecalho[i]);
            ALIAS_DESCRITIVOS.forEach((campo, aliases) -> {
                if (aliases.contains(normalizada)) {
                    encontradas.putIfAbsent(campo, indiceDe(cabecalho, normalizada));
                }
            });
        }
        return encontradas;
    }

    /**
     * Casa colunas com o catalogo de nutrientes. Alem da chave do catalogo,
     * aceita a forma com underscore ("energia_kcal") e a forma com a unidade
     * entre parenteses ("Energia (kcal)"), que sao como as tabelas publicadas
     * costumam nomear.
     */
    private Map<String, Integer> mapearNutrientes(String[] cabecalho) {
        Map<String, String> porNomeNormalizado = new HashMap<>();
        for (Nutriente nutriente : Nutriente.TODOS) {
            porNomeNormalizado.put(normalizar(nutriente.chave()), nutriente.chave());
            porNomeNormalizado.put(
                    normalizar(nutriente.rotulo() + nutriente.unidade()), nutriente.chave());
        }

        Map<String, Integer> encontradas = new LinkedHashMap<>();
        for (int i = 0; i < cabecalho.length; i++) {
            String chave = porNomeNormalizado.get(normalizar(cabecalho[i]));
            if (chave != null) {
                encontradas.putIfAbsent(chave, i);
            }
        }
        return encontradas;
    }

    private int indiceDe(String[] cabecalho, String normalizada) {
        for (int i = 0; i < cabecalho.length; i++) {
            if (normalizar(cabecalho[i]).equals(normalizada)) {
                return i;
            }
        }
        return -1;
    }

    /** Remove acentos, espacos, underscores, hifens e parenteses; devolve minusculo. */
    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String valor(String[] campos, Integer indice) {
        if (indice == null || indice < 0 || indice >= campos.length) {
            return null;
        }
        String bruto = campos[indice].trim();
        return bruto.isEmpty() ? null : bruto;
    }

    private String recortar(String texto, int limite) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= limite ? texto : texto.substring(0, limite);
    }

    /** Aceita virgula decimal, comum em planilhas brasileiras. */
    private BigDecimal numero(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return new BigDecimal(texto.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
