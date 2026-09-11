package com.example.ikimina.dto;

/**
 * Response for group creation. Carries the group admin's one-time password so
 * the super admin can hand it over out of band - it is never written to the
 * application log and is not retrievable again.
 */
public record GroupCreatedDTO(
        Long groupId,
        String name,
        String description,
        String adminEmail,
        String adminTemporaryPassword) {
}
