package rw.ikimina.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import rw.ikimina.support.GroupFixture;
import rw.ikimina.support.GroupFixture.TestGroup;
import rw.ikimina.support.IntegrationTest;
import rw.ikimina.support.PostgresTestDatabase;

/**
 * Spec 5.3: row-level security as the last line of defence. Goes straight to PostgreSQL as
 * the application role - no guard, no permission check, no service - and shows that the
 * database itself confines every query to the tenant set for the transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RowLevelSecurityIT extends IntegrationTest {

    private static final String DB = "app_it";
    private static final List<String> TENANT_TABLES = List.of(
            "group_settings", "group_memberships", "group_invitations", "office_transfers", "settings_change_requests");

    private TestGroup alpha;
    private TestGroup bravo;
    private long alphaId;
    private long bravoId;
    private long alphaPresidentUserId;

    @BeforeAll
    void groups() throws SQLException {
        GroupFixture fixture = new GroupFixture(api(), sms);
        alpha = fixture.createWithEveryRole("Alpha RLS");
        bravo = fixture.createWithEveryRole("Bravo RLS");
        alphaId = internalGroupId(alpha.groupId());
        bravoId = internalGroupId(bravo.groupId());
        alphaPresidentUserId = internalUserId(alpha.president().phone());
    }

    @Test
    void withoutATenantTheApplicationRoleSeesNoGroupData() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            for (String table : TENANT_TABLES) {
                assertThat(count(app, "SELECT count(*) FROM " + table)).as(table).isZero();
            }
            assertThat(count(app, "SELECT count(*) FROM groups")).isZero();
            assertThat(count(app, "SELECT count(*) FROM audit_logs WHERE group_id IS NOT NULL")).isZero();
            app.rollback();
        }
    }

    @Test
    void insideAGroupOnlyThatGroupsRowsExist() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            setTenant(app, alphaId, null);
            for (String table : TENANT_TABLES) {
                assertThat(count(app, "SELECT count(*) FROM " + table + " WHERE group_id <> " + alphaId)).as(table).isZero();
            }
            assertThat(count(app, "SELECT count(*) FROM group_memberships")).isEqualTo(5);
            assertThat(count(app, "SELECT count(*) FROM group_memberships WHERE group_id = " + bravoId)).isZero();
            assertThat(count(app, "SELECT count(*) FROM groups WHERE id = " + bravoId)).isZero();
            assertThat(count(app, "SELECT count(*) FROM audit_logs")).isEqualTo(count(app,
                    "SELECT count(*) FROM audit_logs WHERE group_id = " + alphaId));
            app.rollback();
        }
    }

    @Test
    void writesIntoAnotherGroupAreRefusedOrMatchNothing() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            setTenant(app, alphaId, null);
            try (Statement statement = app.createStatement()) {
                assertThat(statement.executeUpdate("UPDATE groups SET name = 'hijacked' WHERE id = " + bravoId)).isZero();
                assertThat(statement.executeUpdate("UPDATE group_memberships SET role = 'MEMBER' WHERE group_id = " + bravoId)).isZero();
            }
            assertThatThrownBy(() -> {
                try (Statement statement = app.createStatement()) {
                    statement.executeUpdate("INSERT INTO group_memberships (public_id, group_id, user_id, member_number, role) "
                            + "VALUES ('" + UUID.randomUUID() + "', " + bravoId + ", " + alphaPresidentUserId + ", '999', 'PRESIDENT')");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
            app.rollback();
        }
    }

    @Test
    void aUserSeesOnlyTheirOwnMembershipsAcrossGroups() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            setTenant(app, null, alphaPresidentUserId);
            assertThat(count(app, "SELECT count(*) FROM group_memberships WHERE user_id <> " + alphaPresidentUserId)).isZero();
            assertThat(count(app, "SELECT count(*) FROM group_memberships")).isEqualTo(1);
            assertThat(count(app, "SELECT count(*) FROM groups")).isEqualTo(1);
            assertThat(count(app, "SELECT count(*) FROM group_settings")).isZero();
            app.rollback();
        }
    }

    @Test
    void theTenantIsForgottenWhenTheTransactionEnds() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            setTenant(app, alphaId, null);
            assertThat(count(app, "SELECT count(*) FROM group_memberships")).isPositive();
            app.commit();
            // Same pooled-style connection, next transaction: nothing carried over.
            assertThat(count(app, "SELECT count(*) FROM group_memberships")).isZero();
            app.rollback();
        }
    }

    @Test
    void theNarrowExitsAdmitOnlyWhatTheyShould() throws SQLException {
        try (Connection app = PostgresTestDatabase.connectAsApp(DB)) {
            app.setAutoCommit(false);
            assertThatThrownBy(() -> count(app, "SELECT count(*) FROM create_group('anonymous')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("authenticated user");
            app.rollback();
            try (PreparedStatement lookup = app.prepareStatement("SELECT find_group_for_invitation(?, ?)")) {
                lookup.setObject(1, UUID.fromString(bravo.groupId()));
                lookup.setString(2, "0".repeat(64));
                try (ResultSet rows = lookup.executeQuery()) {
                    rows.next();
                    assertThat(rows.getObject(1)).as("a wrong token reveals no group").isNull();
                }
            }
            app.rollback();
        }
    }

    private static void setTenant(Connection connection, Long groupId, Long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.current_group_id', ?, true), set_config('app.current_user_id', ?, true)")) {
            statement.setString(1, groupId == null ? "" : groupId.toString());
            statement.setString(2, userId == null ? "" : userId.toString());
            statement.execute();
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static long internalGroupId(String publicId) throws SQLException {
        return ownerLookup("SELECT id FROM groups WHERE public_id = '" + UUID.fromString(publicId) + "'");
    }

    private static long internalUserId(String phone) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DB);
             PreparedStatement statement = owner.prepareStatement("SELECT id FROM users WHERE phone = ?")) {
            statement.setString(1, phone);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long ownerLookup(String sql) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner(DB)) {
            return count(owner, sql);
        }
    }
}
