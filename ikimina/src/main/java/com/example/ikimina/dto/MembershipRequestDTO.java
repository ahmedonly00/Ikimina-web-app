package com.example.ikimina.dto;

import java.time.LocalDateTime;

import com.example.ikimina.enums.MembershipRequestStatus;
import com.example.ikimina.model.MembershipRequest;

/**
 * A request to join, as an administrator sees it.
 *
 * Carries enough to decide - who is asking and when - without exposing the
 * applicant's whole user record.
 */
public record MembershipRequestDTO(Long id,
                                   Long userId,
                                   String applicantName,
                                   String applicantEmail,
                                   String applicantPhone,
                                   Long savingsGroupId,
                                   MembershipRequestStatus status,
                                   LocalDateTime requestedAt,
                                   LocalDateTime decidedAt) {

    public static MembershipRequestDTO from(MembershipRequest request) {
        var user = request.getUser();
        String name = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : String.join(" ",
                        user.getFirstName() == null ? "" : user.getFirstName(),
                        user.getLastName() == null ? "" : user.getLastName()).trim();

        return new MembershipRequestDTO(
                request.getId(),
                user.getId(),
                name,
                user.getEmail(),
                user.getPhoneNumber(),
                request.getSavingsGroup().getId(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getDecidedAt());
    }
}
