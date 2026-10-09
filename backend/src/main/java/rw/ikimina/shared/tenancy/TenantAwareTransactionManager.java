package rw.ikimina.shared.tenancy;

import java.sql.SQLException;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The application's transaction manager: a JPA transaction manager that, at the start of
 * every transaction, tells PostgreSQL which tenant it is acting for (spec 5.2 step 5).
 *
 * <p>Because this sits in the transaction manager, every path into the database - JPA
 * repositories, JdbcTemplate, nested REQUIRES_NEW transactions - gets the tenant set
 * without any calling code having to remember.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    private static final long serialVersionUID = 1L;

    public TenantAwareTransactionManager(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        super(entityManagerFactory);
        setDataSource(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        DataSource dataSource = getDataSource();
        ConnectionHolder holder = dataSource == null ? null
                : (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        if (holder == null) {
            // Without the JDBC connection exposed, RLS would silently see no tenant: refuse instead.
            throw new TransactionSystemException("JPA transaction did not expose its JDBC connection; tenant cannot be set");
        }
        try {
            TenantSession.apply(holder.getConnection());
        } catch (SQLException e) {
            throw new TransactionSystemException("Could not set tenant on the database session", e);
        }
    }
}
