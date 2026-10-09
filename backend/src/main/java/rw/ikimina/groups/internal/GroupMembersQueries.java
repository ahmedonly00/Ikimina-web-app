package rw.ikimina.groups.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rw.ikimina.groups.GroupMembers;
import rw.ikimina.identity.UserDirectory;
import rw.ikimina.identity.UserDirectory.UserSummary;
import rw.ikimina.shared.tenancy.TenantContext;

@Component
@Transactional(readOnly = true)
class GroupMembersQueries implements GroupMembers {

    private final MembershipRepository memberships;
    private final UserDirectory users;

    GroupMembersQueries(MembershipRepository memberships, UserDirectory users) {
        this.memberships = memberships;
        this.users = users;
    }

    @Override
    public Optional<Member> find(UUID memberId) {
        return memberships.findByGroupIdAndPublicId(groupId(), memberId).map(this::member);
    }

    @Override
    public Optional<Member> findById(long membershipId) {
        return memberships.findById(membershipId).filter(m -> m.getGroupId() == groupId()).map(this::member);
    }

    @Override
    public Map<Long, Member> findByIds(Collection<Long> membershipIds) {
        long groupId = groupId();
        List<Membership> found = memberships.findAllById(membershipIds).stream().filter(m -> m.getGroupId() == groupId).toList();
        Map<Long, UserSummary> people = users.findByIds(found.stream().map(Membership::getUserId).toList());
        return found.stream().collect(Collectors.toMap(Membership::getId, m -> member(m, people.get(m.getUserId())), (a, b) -> a));
    }

    @Override
    public List<Member> active() {
        List<Membership> found = memberships.findByGroupIdAndStatus(groupId(), Membership.Status.ACTIVE, Sort.by("memberNumber"));
        Map<Long, UserSummary> people = users.findByIds(found.stream().map(Membership::getUserId).toList());
        return found.stream().map(m -> member(m, people.get(m.getUserId()))).toList();
    }

    private Member member(Membership membership) {
        return member(membership, users.findById(membership.getUserId()).orElse(null));
    }

    private static Member member(Membership m, UserSummary person) {
        return new Member(m.getId(), m.getPublicId(), m.getUserId(), m.getMemberNumber(), person == null ? null : person.fullName(),
                m.getRole(), m.isActive(), m.getJoinedAt());
    }

    private static long groupId() {
        return TenantContext.requireGroup().groupId();
    }
}
