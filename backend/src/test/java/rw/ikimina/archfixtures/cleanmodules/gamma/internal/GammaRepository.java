package rw.ikimina.archfixtures.cleanmodules.gamma.internal;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

@NoRepositoryBean
public interface GammaRepository extends Repository<Object, Long> {
}
