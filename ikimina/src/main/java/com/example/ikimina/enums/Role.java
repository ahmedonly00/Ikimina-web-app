package com.example.ikimina.enums;

import java.util.Arrays;
import java.util.Optional;

public enum Role {
    ROLE_SUPER_ADMIN(1L),
    ROLE_GROUP_ADMIN(2L),
    ROLE_USER(3L);

    private final Long id;

    Role(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public static Optional<Role> fromId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(role -> role.getId().equals(id))
                .findFirst();
    }
}