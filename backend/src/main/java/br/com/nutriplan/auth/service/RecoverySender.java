package br.com.nutriplan.auth.service;

/**
 * Delivers the recovery link to the user.
 *
 * It is an interface, and not a direct call to a mail server, because
 * integration with messaging is declared out of scope for this work. The
 * recovery flow — drawing, expiry, single use, session invalidation — is what
 * matters here and it is complete; the delivery channel is the seam left open.
 *
 * Swapping in SMTP, a transactional service or WhatsApp is implementing this
 * interface, without touching anything else.
 */
public interface RecoverySender {

    /**
     * @param email          recipient
     * @param name           name of whoever asked, for the message text
     * @param token          the token in the clear — the only time it exists
     * @param expiryMinutes  how long the link is good for
     */
    void send(String email, String name, String token, long expiryMinutes);
}
