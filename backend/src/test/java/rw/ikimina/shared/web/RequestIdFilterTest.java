package rw.ikimina.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RequestIdFilterTest {

    @Test
    void keepsASafeClientSuppliedId() {
        assertThat(RequestIdFilter.sanitise("  mobile-7f3a:req.42  ")).isEqualTo("mobile-7f3a:req.42");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"has space", "line\nbreak", "{\"json\":1}", "quote\"", "ünïcode"})
    void replacesAnUnsafeOrMissingIdWithAFreshUuid(String candidate) {
        String id = RequestIdFilter.sanitise(candidate);
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void replacesAnOverlongId() {
        assertThat(RequestIdFilter.sanitise("a".repeat(65))).hasSize(36);
        assertThat(RequestIdFilter.sanitise("a".repeat(64))).hasSize(64);
    }
}
