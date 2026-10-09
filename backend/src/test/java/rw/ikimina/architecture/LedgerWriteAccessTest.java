package rw.ikimina.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Spec 7.1 #5: "No other class writes to ledger tables". ArchUnit already keeps every other module
 * out of {@code ledger.internal}; this closes the remaining gap - SQL in another module that names
 * a ledger table directly, which bytecode analysis cannot see.
 */
class LedgerWriteAccessTest {

    private static final Path SOURCES = Path.of("src/main/java/rw/ikimina");
    private static final Path LEDGER = SOURCES.resolve("ledger");
    private static final Pattern LEDGER_TABLE = Pattern.compile("\\bledger_(accounts|journals|lines|balances|reversal_requests)\\b");

    @Test
    void onlyTheLedgerModuleMentionsLedgerTables() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(LEDGER))
                    .filter(LedgerWriteAccessTest::mentionsLedgerTable)
                    .map(Path::toString)
                    .toList();
        }
        assertThat(offenders).as("code outside rw.ikimina.ledger referring to ledger tables").isEmpty();
    }

    @Test
    void theCheckWouldNoticeAnOffender() {
        assertThat(LEDGER_TABLE.matcher("UPDATE ledger_balances SET balance = 0").find()).isTrue();
        assertThat(LEDGER_TABLE.matcher("ledgerService.post(request)").find()).isFalse();
    }

    private static boolean mentionsLedgerTable(Path file) {
        try {
            return LEDGER_TABLE.matcher(Files.readString(file, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
