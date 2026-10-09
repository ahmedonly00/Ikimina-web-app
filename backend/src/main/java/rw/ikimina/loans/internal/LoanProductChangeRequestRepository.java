package rw.ikimina.loans.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanProductChangeRequestRepository extends JpaRepository<LoanProductChangeRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LoanProductChangeRequest c where c.groupId = :groupId and c.publicId = :publicId")
    Optional<LoanProductChangeRequest> findByGroupIdAndPublicIdForUpdate(@Param("groupId") Long groupId,
                                                                         @Param("publicId") UUID publicId);

    List<LoanProductChangeRequest> findByGroupIdAndProductIdAndStatus(Long groupId, Long productId,
                                                                      LoanProductChangeRequest.Status status);
}
