package rw.ikimina.shared.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Spring Security's view of an authenticated caller. */
public final class IkiminaAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final AuthenticatedUser user;

    public IkiminaAuthentication(AuthenticatedUser user) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + user.platformRole())));
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    /** Bearer tokens are not kept after authentication. */
    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IkiminaAuthentication other && user.equals(other.user);
    }

    @Override
    public int hashCode() {
        return user.hashCode();
    }
}
