package rw.ikimina.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import rw.ikimina.shared.phone.PhoneNumber;

/** The identity module's public API: read-only facts about people, for other modules. */
public interface UserDirectory {

    record UserSummary(long id, UUID publicId, PhoneNumber phone, String fullName, String locale) {
    }

    Optional<UserSummary> findById(long userId);

    Optional<UserSummary> findByPhone(PhoneNumber phone);

    Map<Long, UserSummary> findByIds(Collection<Long> userIds);
}
