package rw.ikimina.shared.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns the subject of a verified access token into the current user, checking that the
 * account still exists, is active, and has not changed credentials since the token was
 * issued. Implemented by the identity module; declared here so shared security code does
 * not depend on any module.
 */
public interface AuthenticatedUserResolver {

    Optional<AuthenticatedUser> resolve(UUID publicId, Instant issuedAt, Instant authTime);
}
