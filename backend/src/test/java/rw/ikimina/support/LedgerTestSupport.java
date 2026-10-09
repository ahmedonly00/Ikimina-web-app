package rw.ikimina.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import rw.ikimina.shared.security.AuthenticatedUser;
import rw.ikimina.shared.security.IkiminaAuthentication;
import rw.ikimina.shared.tenancy.TenantContext;

/** Runs code as a given member of a group, inside a transaction, for tests that call services directly. */
public final class LedgerTestSupport {

    /** Internal ids of a member, read as the schema owner. */
    public record Actor(long groupId, long membershipId, long userId, String role) {
    }

    private final TransactionTemplate transaction;

    public LedgerTestSupport(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public <T> T as(Actor actor, Supplier<T> work) {
        SecurityContextHolder.getContext().setAuthentication(new IkiminaAuthentication(
                new AuthenticatedUser(actor.userId(), UUID.randomUUID(), "USER", Instant.now())));
        try {
            return TenantContext.callAs(actor.userId(),
                    new TenantContext.GroupScope(actor.groupId(), null, actor.membershipId(), actor.role()),
                    () -> transaction.execute(status -> work.get()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static Actor actor(String groupPublicId, String memberPublicId) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement statement = owner.prepareStatement("""
                     SELECT g.id, m.id, m.user_id, m.role FROM groups g JOIN group_memberships m ON m.group_id = g.id
                     WHERE g.public_id = ? AND m.public_id = ?""")) {
            statement.setObject(1, UUID.fromString(groupPublicId));
            statement.setObject(2, UUID.fromString(memberPublicId));
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("no such member " + memberPublicId);
                }
                return new Actor(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4));
            }
        }
    }

    public static long bucketId(String bucketPublicId) throws SQLException {
        try (Connection owner = PostgresTestDatabase.connectAsOwner("app_it");
             PreparedStatement statement = owner.prepareStatement("SELECT id FROM savings_buckets WHERE public_id = ?")) {
            statement.setObject(1, UUID.fromString(bucketPublicId));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
