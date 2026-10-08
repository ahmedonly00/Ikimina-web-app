package rw.ikimina;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Entry point of the Ikimina modular monolith.
 *
 * <p>The in-memory user store Spring Boot would otherwise create (and whose
 * generated password it logs) is excluded: authentication arrives in Phase 1
 * as phone + password with JWTs, and until then nothing can log in.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class IkiminaApplication {

    public static void main(String[] args) {
        SpringApplication.run(IkiminaApplication.class, args);
    }
}
