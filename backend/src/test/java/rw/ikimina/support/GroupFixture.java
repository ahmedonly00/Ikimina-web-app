package rw.ikimina.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rw.ikimina.groups.GroupRole;
import rw.ikimina.notifications.internal.FakeSmsProvider;
import rw.ikimina.support.Api.Response;
import rw.ikimina.support.Api.User;

/** Builds groups through the public API, exactly as real users would. */
public final class GroupFixture {

    private static final Pattern INVITE_LINK = Pattern.compile("group=([0-9a-f-]{36})&token=([A-Za-z0-9_-]+)");

    /** A group and who holds which role in it; {@code memberIds} are membership public ids. */
    public record TestGroup(String groupId, Map<GroupRole, User> people, Map<GroupRole, String> memberIds) {

        public User president() {
            return people.get(GroupRole.PRESIDENT);
        }

        public User as(GroupRole role) {
            return people.get(role);
        }

        public String memberId(GroupRole role) {
            return memberIds.get(role);
        }

        public String path(String suffix) {
            return "/api/v1/groups/" + groupId + suffix;
        }
    }

    public record Invite(String groupId, String token) {
    }

    private final Api api;
    private final FakeSmsProvider sms;

    public GroupFixture(Api api, FakeSmsProvider sms) {
        this.api = api;
        this.sms = sms;
    }

    /** A group with only its founding President. */
    public TestGroup create(String name) {
        User president = api.register("President of " + name);
        Response created = api.post("/api/v1/groups", president, Map.of("name", name, "district", "Gasabo")).expect(201);
        Map<GroupRole, User> people = new EnumMap<>(GroupRole.class);
        Map<GroupRole, String> ids = new EnumMap<>(GroupRole.class);
        people.put(GroupRole.PRESIDENT, president);
        ids.put(GroupRole.PRESIDENT, created.text("myMemberId"));
        return new TestGroup(created.text("groupId"), people, ids);
    }

    /** A group with one person in every role: President, Treasurer, Secretary, Auditor, Member. */
    public TestGroup createWithEveryRole(String name) {
        TestGroup group = create(name);
        for (GroupRole role : new GroupRole[] {GroupRole.TREASURER, GroupRole.SECRETARY, GroupRole.AUDITOR, GroupRole.MEMBER}) {
            User person = api.register(role.name().charAt(0) + role.name().substring(1).toLowerCase() + " of " + name);
            String memberId = join(group, person, role == GroupRole.AUDITOR ? GroupRole.AUDITOR : GroupRole.MEMBER);
            if (role.isOffice()) {
                api.patch(group.path("/members/" + memberId), group.president(), Map.of("role", role.name())).expect(200);
            }
            group.people().put(role, person);
            group.memberIds().put(role, memberId);
        }
        return group;
    }

    /** Invites {@code person} and has them accept; returns their member id. */
    public String join(TestGroup group, User person, GroupRole invitedAs) {
        Invite invite = invite(group, person.phone(), invitedAs);
        Response accepted = api.post("/api/v1/groups/" + invite.groupId() + "/invitations/accept", person,
                Map.of("token", invite.token())).expect(200);
        return accepted.text("memberId");
    }

    /** Sends an invitation and reads the link back out of the SMS. */
    public Invite invite(TestGroup group, String phone, GroupRole role) {
        api.post(group.path("/invitations"), group.president(), Map.of("phone", phone, "role", role.name())).expect(201);
        return lastInvite(phone);
    }

    public Invite lastInvite(String phone) {
        String text = sms.sentTo(phone).getFirst().text();
        Matcher matcher = INVITE_LINK.matcher(text);
        assertThat(matcher.find()).as("invitation link in SMS: %s", text).isTrue();
        return new Invite(matcher.group(1), matcher.group(2));
    }
}
