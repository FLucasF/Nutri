package br.com.nutriplan.shared.util;

/**
 * O CRN como ele sai no papel.
 *
 * O cadastro guarda o registro do jeito que o profissional digitou — "CRN-3
 * 45821" na maioria das contas, "45821" em algumas. Prefixar sempre com "CRN"
 * imprimia "CRN CRN-3 45821" na linha de assinatura.
 */
public final class CrnText {

    private CrnText() {
    }

    /** "CRN-3 45821" como veio, ou "CRN 45821" quando o prefixo não foi digitado. */
    public static String of(String crn) {
        if (crn == null || crn.isBlank()) {
            return null;
        }
        String trimmed = crn.trim();
        return trimmed.toUpperCase().startsWith("CRN") ? trimmed : "CRN " + trimmed;
    }
}
