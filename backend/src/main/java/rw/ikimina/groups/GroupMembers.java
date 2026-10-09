package rw.ikimina.groups;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The groups module's public view of memberships, for other modules. Every method acts on the
 * group in the current tenant scope only.
 */
public interface GroupMembers {

    /**
     * @param membershipId internal id (for ledger accounts, foreign keys)
     * @param memberId     the public id clients use
     */
    record Member(long membershipId, UUID memberId, long userId, String memberNumber, String fullName,
                  GroupRole role, boolean active, Instant joinedAt) {
    }

    Optional<Member> find(UUID memberId);

    Optional<Member> findById(long membershipId);

    Map<Long, Member> findByIds(Collection<Long> membershipIds);

    /** Active members, by member number. */
    List<Member> active();
}
