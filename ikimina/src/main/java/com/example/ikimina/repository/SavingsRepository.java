package com.example.ikimina.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ikimina.model.Savings;
import com.example.ikimina.model.User;
import com.example.ikimina.enums.SavingsType;

/**
 * Aggregates return BigDecimal, and use COALESCE so an empty range yields zero
 * rather than null - a null total silently became 0.0 or an NPE at the call site.
 *
 * Every named parameter is bound with @Param rather than relying on the compiler
 * being invoked with -parameters.
 */
@Repository
public interface SavingsRepository extends JpaRepository<Savings, Long> {

    List<Savings> findByUser(User user);

    /** Paged variant for member-facing history, which grows without bound. */
    Page<Savings> findByUser(User user, Pageable pageable);

    List<Savings> findByUserAndDateBetween(User user, LocalDate startDate, LocalDate endDate);

    List<Savings> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Savings s WHERE s.date = :date")
    BigDecimal getTotalSavingsForDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Savings s "
            + "WHERE s.user = :user AND s.date BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSavingsForUserBetweenDates(@Param("user") User user,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(s.amount), 0) FROM Savings s "
            + "WHERE s.user.id = :userId AND s.type = :type "
            + "AND s.date BETWEEN :startDate AND :endDate")
    BigDecimal getTotalByUserTypeBetweenDates(@Param("userId") Long userId,
                                              @Param("type") SavingsType type,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM Savings s WHERE s.user.id IN :userIds "
            + "AND s.date BETWEEN :startDate AND :endDate ORDER BY s.user.id, s.date")
    List<Savings> findByUserIdsAndDateBetween(@Param("userIds") List<Long> userIds,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    /**
     * Every savings row belonging to any member of one group.
     *
     * The admin savings screen had no such query and fell back to
     * "my own savings", so a group admin saw an empty page while the group's
     * ledger balance was non-zero - the dashboard and the savings screen
     * disagreed about the same group.
     */
    @Query("SELECT s FROM Savings s JOIN s.user u JOIN u.memberGroups g "
            + "WHERE g.id = :groupId")
    Page<Savings> findByGroupId(@Param("groupId") Long groupId, Pageable pageable);

    /**
     * As above, restricted to a date range.
     *
     * The savings screen sums the rows it received to produce a weekly total.
     * Over an unbounded page that sum silently reports whatever fitted on page
     * one, so the range has to be pushed into the query rather than applied
     * after paging.
     */
    @Query("SELECT s FROM Savings s JOIN s.user u JOIN u.memberGroups g "
            + "WHERE g.id = :groupId AND s.date BETWEEN :startDate AND :endDate")
    Page<Savings> findByGroupIdAndDateBetween(@Param("groupId") Long groupId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate,
                                              Pageable pageable);

    @Query("SELECT s FROM Savings s WHERE s.date = :date AND s.user.id = :userId AND s.type = :type")
    Savings findByDateAndUserAndType(@Param("date") LocalDate date,
                                     @Param("userId") Long userId,
                                     @Param("type") SavingsType type);
}
