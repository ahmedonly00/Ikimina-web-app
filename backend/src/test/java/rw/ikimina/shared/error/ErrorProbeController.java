package rw.ikimina.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.ikimina.shared.money.Money;

/**
 * Test-only endpoints that fail in each way the error model must handle.
 * Exists only under the "error-probe" profile, so no other test context gets it.
 */
@RestController
@Profile("error-probe")
@RequestMapping("/test-support/errors")
class ErrorProbeController {

    record Payment(@NotNull Money amount, @NotBlank String reference) {
    }

    @PostMapping("/echo")
    Payment echo(@Valid @RequestBody Payment payment) {
        return payment;
    }

    @GetMapping("/business-rule")
    void businessRule() {
        throw new ApiException(ErrorCode.INSUFFICIENT_GROUP_FUNDS);
    }

    @GetMapping("/bug")
    void bug() {
        throw new IllegalStateException("connection to db-internal.example:5432 failed for user ikimina_app");
    }

    @GetMapping("/denied")
    void denied() {
        throw new AccessDeniedException("not an officer");
    }
}
