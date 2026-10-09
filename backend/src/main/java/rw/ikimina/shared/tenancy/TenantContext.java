package rw.ikimina.shared.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/**
 * The user and group the current thread is acting for (spec 5.2).
 *
 * <p>For a request, the user is set after authentication and the group by
 * {@code GroupAccessGuard} once membership is proven. Every transaction then copies
 * both into PostgreSQL ({@link TenantAwareTransactionManager}) so row-level security
 * applies. Scheduled jobs set them explicitly with {@link #callAs}.
 */
public final class TenantContext {

    /**
     * The group being acted in. {@code membershipId} and {@code role} are null when the
     * caller is not (yet) a member - accepting an invitation - or for a system job.
     */
    public record GroupScope(long groupId, UUID groupPublicId, Long membershipId, String role) {
    }

    private static final ThreadLocal<Long> USER = new ThreadLocal<>();
    private static final ThreadLocal<GroupScope> GROUP = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<Long> currentUserId() {
        return Optional.ofNullable(USER.get());
    }

    public static Optional<GroupScope> currentGroup() {
        return Optional.ofNullable(GROUP.get());
    }

    /** The current group; reaching here without one is a programming error, reported as 404 rather than leaking. */
    public static GroupScope requireGroup() {
        GroupScope scope = GROUP.get();
        if (scope == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return scope;
    }

    public static void setUser(Long userId) {
        USER.set(userId);
    }

    /**
     * Enters a group for the rest of this thread's work. Inside an open transaction, use
     * {@code TenantSession.enterGroup} instead, which also updates the database session.
     */
    public static void setGroup(GroupScope scope) {
        GROUP.set(scope);
    }

    public static void clear() {
        USER.remove();
        GROUP.remove();
    }

    /** Runs {@code work} as the given user and group, restoring whatever was set before. */
    public static <T> T callAs(Long userId, GroupScope group, Supplier<T> work) {
        Long previousUser = USER.get();
        GroupScope previousGroup = GROUP.get();
        USER.set(userId);
        GROUP.set(group);
        try {
            return work.get();
        } finally {
            restore(USER, previousUser);
            restore(GROUP, previousGroup);
        }
    }

    private static <V> void restore(ThreadLocal<V> holder, V previous) {
        if (previous == null) {
            holder.remove();
        } else {
            holder.set(previous);
        }
    }
}
