package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.Perfil;
import br.com.nutriplan.auth.domain.Plano;
import br.com.nutriplan.auth.domain.Usuario;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Principal do Spring Security. Carrega id do usuario, id da conta e plano
 * para que servicos e filtros nao precisem reconsultar o banco a cada request.
 */
@Getter
public class UsuarioAutenticado implements UserDetails {

    private final Long usuarioId;
    private final Long contaId;
    private final String email;
    private final String nome;
    private final Perfil perfil;
    private final Plano plano;
    private final String senhaHash;
    private final boolean ativo;
    /** Versao da senha: token com versao diferente nao vale mais. */
    private final int senhaVersao;

    public UsuarioAutenticado(Usuario u) {
        this.usuarioId = u.getId();
        this.contaId = u.getConta().getId();
        this.email = u.getEmail();
        this.nome = u.getNome();
        this.perfil = u.getPerfil();
        this.plano = u.getConta().getPlano();
        this.senhaHash = u.getSenhaHash();
        this.ativo = u.isAtivo() && u.getConta().isAtiva();
        this.senhaVersao = u.getSenhaVersao();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(perfil.authority()));
    }

    @Override public String getPassword()             { return senhaHash; }
    @Override public String getUsername()             { return email; }
    @Override public boolean isEnabled()              { return ativo; }
    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
}
