package rw.ikimina.archfixtures.modules.beta;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/** Violation: a repository outside its module's internal package. Never instantiated by Spring. */
@NoRepositoryBean
public interface BetaRepository extends Repository<Object, Long> {
}
