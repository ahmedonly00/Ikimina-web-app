package com.example.ikimina.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ikimina.dto.MembershipRequestDTO;
import com.example.ikimina.enums.MembershipRequestStatus;
import com.example.ikimina.exception.BusinessRuleException;
import com.example.ikimina.exception.ResourceNotFoundException;
import com.example.ikimina.model.MembershipRequest;
import com.example.ikimina.model.SavingsGroup;
import com.example.ikimina.model.User;
import com.example.ikimina.repository.MembershipRequestRepository;
import com.example.ikimina.repository.SavingsGroupRepository;
import com.example.ikimina.repository.UserRepository;

/**
 * The only ways into a savings group.
 *
 * Registration used to accept a savingsGroupId from an unauthenticated caller
 * and put them straight into that group, so anyone who guessed an id joined a
 * group whose money they had nothing to do with.
 *
 * The rule this class exists to hold is: <strong>the group a person lands in
 * is never chosen by an unauthenticated caller</strong>. It comes from an
 * administrator who already administers it, from an invite code that resolves
 * to exactly one group, or from an administrator approving a request. A
 * pending request grants nothing until it is approved.
 */
@Service
public class GroupMembershipService {

    /*
     * Unambiguous alphabet: no O/0, I/1, or similar, because these codes get
     * read aloud, written on paper and typed by hand.
     */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SavingsGroupRepository savingsGroupRepository;
    private final MembershipRequestRepository membershipRequestRepository;
    private final UserRepository userRepository;

    public GroupMembershipService(SavingsGroupRepository savingsGroupRepository,
                                  MembershipRequestRepository membershipRequestRepository,
                                  UserRepository userRepository) {
        this.savingsGroupRepository = savingsGroupRepository;
        this.membershipRequestRepository = membershipRequestRepository;
        this.userRepository = userRepository;
    }

    // ---- Invite codes -------------------------------------------------

    /**
     * Generate or replace a group's invite code.
     *
     * Rotating invalidates the previous code, which is the point: it is how an
     * administrator stops a code that has been shared too widely.
     */
    @Transactional
    public String rotateJoinCode(Long groupId) {
        SavingsGroup group = requireGroup(groupId);
        group.setJoinCode(uniqueCode());
        savingsGroupRepository.save(group);
        return group.getJoinCode();
    }

    @Transactional
    public void clearJoinCode(Long groupId) {
        SavingsGroup group = requireGroup(groupId);
        group.setJoinCode(null);
        savingsGroupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public String currentJoinCode(Long groupId) {
        return requireGroup(groupId).getJoinCode();
    }

    private String uniqueCode() {
        // Collisions are vanishingly unlikely at 32^10, but the column is
        // UNIQUE and a duplicate would surface as a constraint violation at
        // flush time rather than anything actionable, so check first.
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (savingsGroupRepository.findByJoinCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessRuleException("Could not generate a unique invite code; try again");
    }

    /**
     * Resolve the group an invite code belongs to.
     *
     * A blank code is rejected before the lookup: it must never be allowed to
     * match a group whose code is unset.
     */
    @Transactional(readOnly = true)
    public SavingsGroup groupForJoinCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("An invite code is required");
        }
        SavingsGroup group = savingsGroupRepository.findByJoinCode(code.trim().toUpperCase())
                .orElseThrow(() -> new BusinessRuleException("That invite code is not valid"));
        assertJoinable(group);
        return group;
    }

    // ---- Requests to join ---------------------------------------------

    /**
     * Record a request. Deliberately does not touch group membership - the
     * person is in no group until an administrator approves.
     */
    @Transactional
    public MembershipRequest requestToJoin(Long userId, Long groupId) {
        SavingsGroup group = requireGroup(groupId);
        assertJoinable(group);

        // Loaded inside this transaction: an entity handed across a service
        // boundary is detached, and with open-in-view off its associations
        // cannot initialise.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (userRepository.existsByIdAndSavingsGroupId(userId, groupId)) {
            throw new BusinessRuleException("You are already a member of this group");
        }

        membershipRequestRepository
                .findByUserIdAndSavingsGroupIdAndStatus(userId, groupId,
                        MembershipRequestStatus.PENDING)
                .ifPresent(existing -> {
                    throw new BusinessRuleException("You already have a request awaiting a decision");
                });

        MembershipRequest request = new MembershipRequest();
        request.setUser(user);
        request.setSavingsGroup(group);
        request.setStatus(MembershipRequestStatus.PENDING);
        request.setRequestedAt(LocalDateTime.now());
        return membershipRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<MembershipRequestDTO> pendingRequests(Long groupId) {
        return membershipRequestRepository
                .findByGroupAndStatus(groupId, MembershipRequestStatus.PENDING)
                .stream()
                .map(MembershipRequestDTO::from)
                .toList();
    }

    /** Approving is what creates the membership. */
    @Transactional
    public MembershipRequestDTO approve(Long requestId, Long decidedBy) {
        MembershipRequest request = requireDecidableRequest(requestId);
        SavingsGroup group = request.getSavingsGroup();
        User user = request.getUser();

        assertJoinable(group);

        user.addMemberGroup(group);

        /*
         * Someone who registered by asking had no group yet, so they were
         * issued the group-less "M00009" form. Now that they belong somewhere,
         * give them the group's own numbering - otherwise the roster shows one
         * member numbered unlike every other.
         */
        if (user.getMemberNumber() == null || !user.getMemberNumber().startsWith("G")) {
            user.setMemberNumber(nextGroupMemberNumber(group.getId()));
        }

        userRepository.save(user);

        request.setStatus(MembershipRequestStatus.APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy);
        return MembershipRequestDTO.from(membershipRequestRepository.save(request));
    }

    @Transactional
    public MembershipRequestDTO reject(Long requestId, Long decidedBy, String note) {
        MembershipRequest request = requireDecidableRequest(requestId);
        request.setStatus(MembershipRequestStatus.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(decidedBy);
        request.setDecisionNote(note);
        return MembershipRequestDTO.from(membershipRequestRepository.save(request));
    }

    /** The group a request belongs to, for the authorisation check. */
    @Transactional(readOnly = true)
    public Long groupIdForRequest(Long requestId) {
        return membershipRequestRepository.findById(requestId)
                .map(r -> r.getSavingsGroup().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));
    }

    private MembershipRequest requireDecidableRequest(Long requestId) {
        MembershipRequest request = membershipRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));
        if (request.getStatus() != MembershipRequestStatus.PENDING) {
            // Deciding twice would otherwise re-add a member who was removed
            // after the first approval.
            throw new BusinessRuleException("That request has already been decided");
        }
        return request;
    }

    /**
     * Next free member number in a group's own series.
     *
     * Steps past anything already issued: the count moves when a member is
     * removed, and member_number is UNIQUE, so a derived number can collide.
     */
    private String nextGroupMemberNumber(Long groupId) {
        int taken = userRepository.findBySavingsGroupId(groupId).size();
        for (int offset = 1; offset <= 1000; offset++) {
            String candidate = String.format("G%dM%04d", groupId, taken + offset);
            if (!userRepository.existsByMemberNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessRuleException("Could not allocate a member number");
    }

    private SavingsGroup requireGroup(Long groupId) {
        return savingsGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings group not found: " + groupId));
    }

    private void assertJoinable(SavingsGroup group) {
        if (Boolean.FALSE.equals(group.getIsActive())) {
            throw new BusinessRuleException("That group is not active");
        }
        if (Boolean.TRUE.equals(group.getIsSuspended())) {
            throw new BusinessRuleException("That group is suspended");
        }
    }
}
