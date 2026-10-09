package rw.ikimina.shared.security;

import java.time.Instant;
import java.util.UUID;

/**
 * The caller of the current request, resolved from a verified access token.
 *
 * @param id           internal user id (never exposed in URLs or responses)
 * @param publicId     the id clients see
 * @param platformRole USER or PLATFORM_ADMIN; group roles live on memberships
 * @param authTime     when the user last entered their password (step-up checks, spec 16.1)
 */
public record AuthenticatedUser(long id, UUID publicId, String platformRole, Instant authTime) {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public boolean isPlatformAdmin() {
        return PLATFORM_ADMIN.equals(platformRole);
    }
}
