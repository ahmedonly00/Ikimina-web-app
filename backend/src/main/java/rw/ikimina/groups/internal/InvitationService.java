package rw.ikimina.groups.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.identity.UserDirectory;
import rw.ikimina.identity.UserDirectory.UserSummary;
import rw.ikimina.notifications.SmsNotifier;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.phone.PhoneNumber;
import rw.ikimina.shared.security.CurrentUser;
import rw.ikimina.shared.tenancy.TenantContext;
import rw.ikimina.shared.tenancy.TenantSession;
import tools.jackson.databind.json.JsonMapper;

/**
 * Inviting people by phone and accepting invitations (spec 17.2).
 *
 * <p>The invitation link carries a 256-bit random token; only its hash is stored. Accepting
 * requires being signed in with the very phone number that was invited, so a forwarded
 * link is useless to anyone else. Invitations expire after seven days and are single-use.
 */
@Service
class InvitationService {

    static final Duration VALIDITY = Duration.ofDays(7);

    record InvitationView(UUID invitationId, String phone, GroupRole role, Instant expiresAt, Instant createdAt) {
    }

    record Accepted(UUID groupId, UUID memberId, GroupRole role) {
    }

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final GroupRepository groups;
    private final GroupSettingsRepository settings;
    private final UserDirectory users;
    private final SmsNotifier sms;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final TenantSession tenantSession;
    private final JsonMapper json;
    private final Clock clock;
    private final String webBaseUrl;
    private final SecureRandom random = new SecureRandom();

    InvitationService(InvitationRepository invitations, MembershipRepository memberships, GroupRepository groups,
                      GroupSettingsRepository settings, UserDirectory users, SmsNotifier sms, AuditService audit,
                      JdbcTemplate jdbc, TenantSession tenantSession, JsonMapper json, Clock clock,
                      @Value("${ikimina.web.base-url}") String webBaseUrl) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.groups = groups;
        this.settings = settings;
        this.users = users;
        this.sms = sms;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tenantSession = tenantSession;
        this.json = json;
        this.clock = clock;
        this.webBaseUrl = webBaseUrl;
    }

    @Transactional
    InvitationView invite(String rawPhone, GroupRole role) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        if (role != GroupRole.MEMBER && role != GroupRole.AUDITOR) {
            // Offices are assigned to existing members, never handed out by invitation.
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        PhoneNumber phone = PhoneNumber.parse(rawPhone);
        Optional<UserSummary> existing = users.findByPhone(phone);
        if (existing.isPresent()) {
            memberships.findByGroupIdAndUserId(scope.groupId(), existing.get().id())
                    .filter(m -> !m.hasLeft())
                    .ifPresent(m -> {
                        throw new ApiException(ErrorCode.ALREADY_MEMBER);
                    });
        }
        Instant now = clock.instant();
        long inviter = CurrentUser.require().id();
        // A fresh invitation replaces any earlier open one for the same number.
        for (Invitation open : invitations.findOpenForPhone(scope.groupId(), phone.e164())) {
            open.revoke(inviter, now);
        }
        String token = newToken();
        Invitation invitation = invitations.save(new Invitation(scope.groupId(), phone.e164(), role, hash(token),
                now.plus(VALIDITY), inviter, now));
        audit.record(AuditEvent.of("MEMBER_INVITED").entity("invitation", invitation.getPublicId())
                .after(Map.of("phone", phone.masked(), "role", role)));

        Group group = groups.findById(scope.groupId()).orElseThrow();
        String link = webBaseUrl + "/invitations/accept?group=" + scope.groupPublicId() + "&token=" + token;
        sms.send(phone, Locale.of(existing.map(UserSummary::locale).orElseGet(() -> groupLocale(scope.groupId()))),
                "invite_to_group", group.getName(), link);
        return view(invitation);
    }

    @Transactional(readOnly = true)
    List<InvitationView> pending() {
        return invitations.findPending(TenantContext.requireGroup().groupId(), clock.instant()).stream().map(InvitationService::view).toList();
    }

    @Transactional
    void revoke(UUID invitationId) {
        TenantContext.GroupScope scope = TenantContext.requireGroup();
        Invitation invitation = invitations.findByGroupIdAndPublicIdForUpdate(scope.groupId(), invitationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        invitation.revoke(CurrentUser.require().id(), clock.instant());
        audit.record(AuditEvent.of("INVITATION_REVOKED").entity("invitation", invitation.getPublicId()));
    }

    /**
     * Joins the group named in the link. The caller is usually not a member yet, so cannot
     * see the group; the token itself is what lets {@code find_group_for_invitation} reveal
     * which group it belongs to.
     */
    @Transactional
    Accepted accept(UUID groupPublicId, String token) {
        long userId = CurrentUser.require().id();
        String tokenHash = hash(token);
        Long groupId = jdbc.queryForObject("SELECT find_group_for_invitation(?, ?)", Long.class, groupPublicId, tokenHash);
        if (groupId == null) {
            throw new ApiException(ErrorCode.INVITATION_INVALID);
        }
        tenantSession.enterGroup(new TenantContext.GroupScope(groupId, groupPublicId, null, null));

        Instant now = clock.instant();
        Invitation invitation = invitations.findByGroupIdAndTokenHashForUpdate(groupId, tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVITATION_INVALID));
        UserSummary me = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!invitation.isUsable(now) || !invitation.getPhone().equals(me.phone().e164())) {
            throw new ApiException(ErrorCode.INVITATION_INVALID);
        }

        Optional<Membership> previous = memberships.findByGroupIdAndUserId(groupId, userId);
        Membership membership;
        if (previous.isPresent()) {
            membership = previous.get();
            if (!membership.hasLeft()) {
                throw new ApiException(ErrorCode.ALREADY_MEMBER);
            }
            membership.rejoin(invitation.getRole(), now);
        } else {
            groups.findByIdForUpdate(groupId).orElseThrow();   // serialise member-number allocation
            membership = memberships.save(Membership.active(groupId, userId,
                    MemberNumbers.format(memberships.countByGroupId(groupId) + 1), invitation.getRole(), now));
        }
        invitation.accept(userId, now);
        memberships.flush();

        tenantSession.enterGroup(new TenantContext.GroupScope(groupId, groupPublicId, membership.getId(), membership.getRole().name()));
        audit.record(AuditEvent.of("MEMBER_JOINED").entity("membership", membership.getPublicId())
                .after(Map.of("role", membership.getRole(), "invitation", invitation.getPublicId(),
                        "memberNumber", membership.getMemberNumber())));
        return new Accepted(groupPublicId, membership.getPublicId(), membership.getRole());
    }

    private String groupLocale(long groupId) {
        return settings.findById(groupId)
                .map(row -> json.readValue(row.getSettings(), GroupSettingsV1.class).defaultLocale())
                .orElse("rw");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw new ApiException(ErrorCode.INVITATION_INVALID);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InvitationView view(Invitation invitation) {
        return new InvitationView(invitation.getPublicId(), invitation.getPhone(), invitation.getRole(),
                invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
