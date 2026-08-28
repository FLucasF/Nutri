package br.com.nutriplan.financeiro.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Escreve um valor monetário por extenso, em português.
 *
 * Recibo tradicionalmente traz o valor escrito além do numérico, e a razão é
 * prática: dificulta adulteração de um dígito. Cobre valores até a casa dos
 * milhões, faixa suficiente para um consultório.
 */
public final class ValorPorExtenso {

    private static final String[] UNIDADES = {
            "", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete",
            "dezoito", "dezenove"
    };

    private static final String[] DEZENAS = {
            "", "", "vinte", "trinta", "quarenta", "cinquenta",
            "sessenta", "setenta", "oitenta", "noventa"
    };

    private static final String[] CENTENAS = {
            "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
            "seiscentos", "setecentos", "oitocentos", "novecentos"
    };

    private ValorPorExtenso() {
    }

    public static String emReais(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_UP);
        long reais = arredondado.longValue();
        int centavos = arredondado.subtract(BigDecimal.valueOf(reais))
                .movePointRight(2).abs().intValue();

        StringBuilder texto = new StringBuilder();
        if (reais > 0) {
            String extenso = porExtenso(reais);
            // Em portugues, ordens de milhao exigem a preposicao: diz-se
            // "um milhao DE reais", mas "mil reais" sem preposicao.
            String ligacao = terminaEmOrdemDeMilhao(extenso) ? " de" : "";
            texto.append(extenso)
                 .append(ligacao)
                 .append(reais == 1 ? " real" : " reais");
        }
        if (centavos > 0) {
            if (texto.length() > 0) {
                texto.append(" e ");
            }
            texto.append(porExtenso(centavos))
                 .append(centavos == 1 ? " centavo" : " centavos");
        }
        return texto.length() == 0 ? "zero reais" : texto.toString();
    }

    private static boolean terminaEmOrdemDeMilhao(String extenso) {
        return extenso.endsWith("milhão") || extenso.endsWith("milhões");
    }

    private static String porExtenso(long numero) {
        if (numero == 0) {
            return "zero";
        }
        if (numero >= 1_000_000) {
            long milhoes = numero / 1_000_000;
            long resto = numero % 1_000_000;
            String texto = (milhoes == 1 ? "um milhão" : porExtenso(milhoes) + " milhões");
            return resto == 0 ? texto : texto + juntar(resto) + porExtenso(resto);
        }
        if (numero >= 1000) {
            long milhares = numero / 1000;
            long resto = numero % 1000;
            String texto = (milhares == 1 ? "mil" : porExtenso(milhares) + " mil");
            return resto == 0 ? texto : texto + juntar(resto) + porExtenso(resto);
        }
        if (numero == 100) {
            return "cem";
        }
        if (numero >= 100) {
            long centena = numero / 100;
            long resto = numero % 100;
            return CENTENAS[(int) centena] + (resto == 0 ? "" : " e " + porExtenso(resto));
        }
        if (numero >= 20) {
            long dezena = numero / 10;
            long resto = numero % 10;
            return DEZENAS[(int) dezena] + (resto == 0 ? "" : " e " + UNIDADES[(int) resto]);
        }
        return UNIDADES[(int) numero];
    }

    /**
     * Liga a ordem maior ao resto. Usa "e" quando o resto é menor que cem ou
     * centena redonda — "mil e duzentos", mas "mil duzentos e cinquenta".
     */
    private static String juntar(long resto) {
        return (resto < 100 || resto % 100 == 0) ? " e " : " ";
    }
}
