package br.com.nutriplan.auth.service;

/**
 * Entrega o link de recuperacao ao usuario.
 *
 * E uma interface, e nao uma chamada direta a um servidor de e-mail, porque a
 * integracao com mensageria esta declarada fora do escopo deste trabalho. O
 * fluxo de recuperacao — sorteio, validade, uso unico, invalidacao de sessao —
 * e o que interessa aqui e esta inteiro; o canal de entrega e a costura
 * deixada aberta.
 *
 * Trocar por SMTP, por servico transacional ou por WhatsApp e implementar esta
 * interface, sem tocar em mais nada.
 */
public interface EnviadorDeRecuperacao {

    /**
     * @param email          destinatario
     * @param nome           nome de quem pediu, para o texto da mensagem
     * @param token          o token em claro — a unica vez em que ele existe
     * @param validadeMinutos por quanto tempo o link vale
     */
    void enviar(String email, String nome, String token, long validadeMinutos);
}
