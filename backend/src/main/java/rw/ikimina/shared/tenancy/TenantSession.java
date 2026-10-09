package rw.ikimina.shared.tenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Copies {@link TenantContext} into the PostgreSQL session that row-level security reads
 * ({@code app.current_group_id}, {@code app.current_user_id}; see V5).
 *
 * <p>Values are set with {@code set_config(..., true)}, i.e. transaction-local: they vanish
 * at commit or rollback, so a pooled connection never carries one request's tenant into
 * the next. Both are always written - empty when absent - so nothing is inherited.
 */
@Component
public class TenantSession {

    private static final String APPLY =
            "SELECT set_config('app.current_group_id', ?, true), set_config('app.current_user_id', ?, true)";

    private final DataSource dataSource;

    public TenantSession(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    static void apply(Connection connection) throws SQLException {
        String group = TenantContext.currentGroup().map(scope -> Long.toString(scope.groupId())).orElse("");
        String user = TenantContext.currentUserId().map(String::valueOf).orElse("");
        try (PreparedStatement statement = connection.prepareStatement(APPLY)) {
            statement.setString(1, group);
            statement.setString(2, user);
            statement.execute();
        }
    }

    /**
     * Switches the current thread and the current transaction to {@code scope}: used once a
     * group's id becomes known mid-transaction (creating a group, accepting an invitation).
     */
    public void enterGroup(TenantContext.GroupScope scope) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("enterGroup must be called inside a transaction");
        }
        TenantContext.setGroup(scope);
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            apply(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not set tenant on the database session", e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
