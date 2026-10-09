package rw.ikimina.shared.tenancy;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Replaces Spring Boot's default JPA transaction manager with the tenant-aware one. */
@Configuration(proxyBeanMethods = false)
public class TransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        return new TenantAwareTransactionManager(entityManagerFactory, dataSource);
    }
}
