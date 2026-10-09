package rw.ikimina.identity.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.identity.UserDirectory;
import rw.ikimina.shared.phone.PhoneNumber;
import rw.ikimina.shared.security.AuthenticatedUser;
import rw.ikimina.shared.security.AuthenticatedUserResolver;

/** Read side of identity: the public {@link UserDirectory} and the token-subject resolver. */
@Component
@Transactional(readOnly = true)
class IdentityQueries implements UserDirectory, AuthenticatedUserResolver {

    private final UserRepository users;

    IdentityQueries(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<UserSummary> findById(long userId) {
        return users.findById(userId).map(IdentityQueries::summary);
    }

    @Override
    public Optional<UserSummary> findByPhone(PhoneNumber phone) {
        return users.findByPhone(phone.e164()).map(IdentityQueries::summary);
    }

    @Override
    public Map<Long, UserSummary> findByIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(userIds).stream()
                .map(IdentityQueries::summary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    /** Called on every authenticated request: the account must still exist, be active, and not have changed password since the token was issued. */
    @Override
    public Optional<AuthenticatedUser> resolve(UUID publicId, Instant issuedAt, Instant authTime) {
        return users.findByPublicId(publicId)
                .filter(User::isActive)
                .filter(user -> user.acceptsTokenIssuedAt(issuedAt))
                .map(user -> new AuthenticatedUser(user.getId(), user.getPublicId(), user.getPlatformRole(), authTime));
    }

    private static UserSummary summary(User user) {
        return new UserSummary(user.getId(), user.getPublicId(), user.getPhone(), user.getFullName(), user.getLocale());
    }
}
