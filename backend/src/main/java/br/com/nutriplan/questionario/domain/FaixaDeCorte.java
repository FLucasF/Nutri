package br.com.nutriplan.questionario.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma faixa de pontuacao e o que ela significa.
 *
 * Vive em texto no formato "0-5=Baixo|6-10=Moderado|11-99=Alto", pela mesma
 * razao das opcoes: e sempre lida inteira, junto do questionario.
 *
 * A faixa usada e copiada para dentro da resposta. Se ficasse so no
 * questionario, corrigir um ponto de corte reclassificaria respostas antigas —
 * e a classificacao de um paciente e o registro do que se concluiu naquele dia.
 */
public record FaixaDeCorte(int minimo, int maximo, String classificacao) {

    public static List<FaixaDeCorte> analisar(String texto) {
        List<FaixaDeCorte> faixas = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return faixas;
        }
        for (String parte : texto.split("\\|")) {
            String limpo = parte.trim();
            int sinal = limpo.indexOf('=');
            int traco = limpo.indexOf('-');
            if (sinal < 0 || traco < 0 || traco > sinal) {
                continue;
            }
            try {
                faixas.add(new FaixaDeCorte(
                        Integer.parseInt(limpo.substring(0, traco).trim()),
                        Integer.parseInt(limpo.substring(traco + 1, sinal).trim()),
                        limpo.substring(sinal + 1).trim()));
            } catch (NumberFormatException ignorado) {
                // Faixa torta nao derruba o questionario: fica sem
                // classificacao, o que e melhor que classificar errado.
            }
        }
        return faixas;
    }

    public static String classificar(String texto, Integer escore) {
        if (escore == null) {
            return null;
        }
        for (FaixaDeCorte faixa : analisar(texto)) {
            if (escore >= faixa.minimo() && escore <= faixa.maximo()) {
                return faixa.classificacao();
            }
        }
        return null;
    }
}
