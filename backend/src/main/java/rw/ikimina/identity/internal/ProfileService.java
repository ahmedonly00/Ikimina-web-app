package rw.ikimina.identity.internal;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.audit.AuditEvent;
import rw.ikimina.audit.AuditService;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;
import rw.ikimina.shared.security.CurrentUser;

/** {@code GET/PATCH /me} (spec 17.1). */
@Service
class ProfileService {

    record Profile(UUID id, String phone, String fullName, String locale, String email, String platformRole) {
    }

    private final UserRepository users;
    private final AuditService audit;

    ProfileService(UserRepository users, AuditService audit) {
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    Profile current() {
        return toProfile(load());
    }

    @Transactional
    Profile update(String fullName, String locale) {
        User user = load();
        Map<String, String> before = Map.of("fullName", user.getFullName(), "locale", user.getLocale());
        user.updateProfile(fullName == null ? null : fullName.trim(), locale);
        audit.record(AuditEvent.of("PROFILE_UPDATED").entity("user", user.getPublicId())
                .before(before).after(Map.of("fullName", user.getFullName(), "locale", user.getLocale())));
        return toProfile(user);
    }

    private User load() {
        return users.findById(CurrentUser.require().id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }

    private static Profile toProfile(User user) {
        return new Profile(user.getPublicId(), user.getPhone().e164(), user.getFullName(), user.getLocale(),
                user.getEmail(), user.getPlatformRole());
    }
}
