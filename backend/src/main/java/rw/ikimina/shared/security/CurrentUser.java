package rw.ikimina.shared.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/** Access to the authenticated caller from service code. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** @throws ApiException UNAUTHENTICATED if nobody is signed in */
    public static AuthenticatedUser require() {
        return optional().orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}
