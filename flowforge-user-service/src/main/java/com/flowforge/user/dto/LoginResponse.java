package com.flowforge.user.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserProfile user
) {
    public static LoginResponse bearer(String token, long expiresIn, UserProfile user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}
