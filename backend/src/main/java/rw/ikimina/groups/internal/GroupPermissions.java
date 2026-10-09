package rw.ikimina.groups.internal;

import java.util.UUID;

import org.springframework.stereotype.Component;
import rw.ikimina.groups.GroupRole;
import rw.ikimina.groups.Permission;
import rw.ikimina.groups.PermissionMatrix;
import rw.ikimina.shared.tenancy.TenantContext;

/**
 * The {@code @perm} bean used in {@code @PreAuthorize} (spec 5.4 #3). Relies on
 * {@link GroupAccessGuard} having established membership for exactly this group; if the
 * scope is missing or for another group, the answer is no.
 */
@Component("perm")
public class GroupPermissions {

    public boolean has(UUID groupId, String permission) {
        GroupRole role = role(groupId);
        return role != null && PermissionMatrix.allows(role, Permission.valueOf(permission));
    }

    public boolean hasAny(UUID groupId, String... permissions) {
        for (String permission : permissions) {
            if (has(groupId, permission)) {
                return true;
            }
        }
        return false;
    }

    /** The caller's role in {@code groupId}, or null if they are not acting as a member of it. */
    static GroupRole role(UUID groupId) {
        return TenantContext.currentGroup()
                .filter(scope -> scope.groupPublicId() != null && scope.groupPublicId().equals(groupId))
                .filter(scope -> scope.role() != null && scope.membershipId() != null)
                .map(scope -> GroupRole.valueOf(scope.role()))
                .orElse(null);
    }
}
