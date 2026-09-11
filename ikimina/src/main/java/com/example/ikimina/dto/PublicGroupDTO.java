package com.example.ikimina.dto;

/**
 * Minimal group projection served without authentication so the login and
 * register screens can populate their group picker. Deliberately exposes only
 * id and name - no membership, admin, or financial data.
 */
public record PublicGroupDTO(Long id, String name) {
}
