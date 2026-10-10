package rw.ikimina.loans.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    Optional<LoanProduct> findByGroupIdAndPublicId(Long groupId, UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LoanProduct p where p.groupId = :groupId and p.publicId = :publicId")
    Optional<LoanProduct> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId, @Param("publicId") UUID publicId);

    Optional<LoanProduct> findByGroupIdAndId(Long groupId, Long id);

    List<LoanProduct> findByGroupIdOrderByName(Long groupId);

    boolean existsByGroupIdAndNameIgnoreCase(Long groupId, String name);
}
