package br.com.nutriplan.auth.domain;

import br.com.nutriplan.shared.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario", uniqueConstraints = @UniqueConstraint(name = "uk_usuario_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Usuario extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false, foreignKey = @ForeignKey(name = "fk_usuario_conta"))
    private Conta conta;

    /** Registro no conselho (CRN). Apenas para perfil NUTRICIONISTA. */
    @Column(length = 30)
    private String crn;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    private boolean ativo = true;

    /**
     * Versao da senha, incrementada a cada troca.
     *
     * A autenticacao e sem estado, entao nao ha sessao para encerrar. O token
     * carrega a versao vigente na emissao; quando a senha muda, o numero muda,
     * e todo token anterior deixa de casar.
     *
     * E um contador e nao um horario de proposito: a data de emissao do JWT
     * tem precisao de segundo, e comparar contra um instante com milissegundos
     * produz os dois erros opostos — deslogar quem acabou de trocar a propria
     * senha, ou manter viva a sessao que se queria derrubar.
     */
    @Column(name = "senha_versao", nullable = false)
    private int senhaVersao = 0;

    /** Auditoria: quando a senha foi trocada pela ultima vez. */
    @Column(name = "senha_alterada_em")
    private java.time.Instant senhaAlteradaEm;

    /** Troca a senha e derruba as sessoes abertas. */
    public void trocarSenha(String novoHash, java.time.Instant quando) {
        this.senhaHash = novoHash;
        this.senhaVersao++;
        this.senhaAlteradaEm = quando;
    }

    public Usuario(String nome, String email, String senhaHash, Perfil perfil, Conta conta) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.conta = conta;
    }
}
