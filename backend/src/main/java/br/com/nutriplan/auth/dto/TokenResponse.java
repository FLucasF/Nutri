package br.com.nutriplan.auth.dto;

import br.com.nutriplan.auth.domain.Role;
import br.com.nutriplan.auth.domain.Plan;

public record TokenResponse(
        String token,
        String type,
        long expiresAtSeconds,
        UserSummary user
) {
    public record UserSummary(Long id, String name, String email, Role role, Long accountId, Plan plan) {}

    public static TokenResponse from(String token, long expiresAt,
                                   br.com.nutriplan.auth.service.AuthenticatedUser u) {
        return new TokenResponse(token, "Bearer", expiresAt, new UserSummary(
                u.getUserId(), u.getName(), u.getEmail(), u.getRole(), u.getAccountId(), u.getPlan()));
    }
}
