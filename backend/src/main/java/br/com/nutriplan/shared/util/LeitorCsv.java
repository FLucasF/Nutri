package br.com.nutriplan.shared.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Divisão de linha CSV que respeita aspas.
 *
 * Existe porque {@code split(",")} quebra silenciosamente em dados reais: as
 * descrições da TACO trazem vírgulas ("Arroz, integral, cozido") e as medidas
 * do IBGE usam vírgula decimal no próprio nome do utensílio ("garrafa (1,5 l)").
 * Nos dois casos o divisor ingênuo produz colunas deslocadas, e a linha é
 * descartada mais adiante por um erro de conversão que não aponta a causa.
 */
public final class LeitorCsv {

    private LeitorCsv() {
    }

    public static String[] dividir(String linha) {
        return dividir(linha, ',');
    }

    public static String[] dividir(String linha, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char ch = linha.charAt(i);
            if (ch == '"') {
                // Aspas duplicadas dentro de campo entre aspas representam
                // uma aspa literal.
                if (entreAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"');
                    i++;
                } else {
                    entreAspas = !entreAspas;
                }
            } else if (ch == separador && !entreAspas) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(ch);
            }
        }
        campos.add(atual.toString());
        return campos.toArray(new String[0]);
    }
}
