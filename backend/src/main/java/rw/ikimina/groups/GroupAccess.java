package rw.ikimina.groups;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares who may call an endpoint under {@code /api/v1/groups/{groupId}} when it is not
 * restricted to a matrix permission with {@code @PreAuthorize}. Every group endpoint must
 * carry one or the other; {@code GroupRouteCoverageIT} fails the build otherwise, so access
 * is always a deliberate, visible decision.
 */
public final class GroupAccess {

    private GroupAccess() {
    }

    /**
     * Any active member. The endpoint still applies object-level rules itself (spec 5.4 #4),
     * e.g. a member may read only their own member record.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface AnyMember {
    }

    /**
     * Callable by someone who is not (yet) a member - only accepting an invitation, whose
     * secret token is the credential. The membership guard does not 404 these.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface NonMemberAllowed {
    }
}
