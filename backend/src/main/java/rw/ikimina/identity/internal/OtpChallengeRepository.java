package rw.ikimina.identity.internal;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByPhoneAndPurposeAndConsumedAtIsNullAndSupersededAtIsNullOrderByIdDesc(
            String phone, OtpChallenge.Purpose purpose);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OtpChallenge c set c.supersededAt = :now
            where c.phone = :phone and c.purpose = :purpose and c.consumedAt is null and c.supersededAt is null""")
    int supersedeOpen(@Param("phone") String phone, @Param("purpose") OtpChallenge.Purpose purpose,
                      @Param("now") Instant now);

    @Modifying
    @Query("delete from OtpChallenge c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
