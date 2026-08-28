package br.com.nutriplan.auth.service;

import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.Plan;
import br.com.nutriplan.auth.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal. It carries the user id, the account id and the
 * plan so that services and filters do not have to query the database again on
 * every request.
 */
@Getter
public class AuthenticatedUser implements UserDetails {

    private final Long userId;
    private final Long accountId;
    private final String email;
    private final String name;
    private final Role role;
    private final Plan plan;
    private final String passwordHash;
    private final boolean active;
    /** Password version: a token with a different version no longer holds. */
    private final int passwordVersion;

    public AuthenticatedUser(User u) {
        this.userId = u.getId();
        this.accountId = u.getAccount().getId();
        this.email = u.getEmail();
        this.name = u.getName();
        this.role = u.getRole();
        this.plan = u.getAccount().getPlan();
        this.passwordHash = u.getPasswordHash();
        this.active = u.isActive() && u.getAccount().isActive();
        this.passwordVersion = u.getPasswordVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override public String getPassword()             { return passwordHash; }
    @Override public String getUsername()             { return email; }
    @Override public boolean isEnabled()              { return active; }
    @Override public boolean isAccountNonExpired()    { return true; }
    @Override public boolean isAccountNonLocked()     { return true; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
}
