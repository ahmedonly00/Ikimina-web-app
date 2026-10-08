package rw.ikimina.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import rw.ikimina.shared.error.ErrorCode;

/** Hard Rule H10: every English message has a Kinyarwanda counterpart, and vice versa. */
class I18nKeyParityTest {

    private static final String PLACEHOLDER_MARKER = "[rw-todo]";
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)[^}]*}");

    private final Properties english = load("i18n/messages.properties");
    private final Properties kinyarwanda = load("i18n/messages_rw.properties");

    @Test
    void bothLanguagesDefineExactlyTheSameKeys() {
        assertThat(kinyarwanda.stringPropertyNames()).isEqualTo(english.stringPropertyNames());
    }

    @Test
    void everyErrorCodeHasATitleAndDetailInBothLanguages() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(english).containsKeys(code.titleKey(), code.detailKey());
            assertThat(kinyarwanda).containsKeys(code.titleKey(), code.detailKey());
        }
    }

    @Test
    void translationsUseTheSameMessageArguments() {
        for (String key : english.stringPropertyNames()) {
            assertThat(arguments(kinyarwanda.getProperty(key)))
                    .as("message arguments of %s", key)
                    .isEqualTo(arguments(english.getProperty(key)));
        }
    }

    @Test
    void noMessageIsBlank() {
        english.forEach((key, value) -> assertThat((String) value).as("en %s", key).isNotBlank());
        kinyarwanda.forEach((key, value) -> assertThat((String) value).as("rw %s", key).isNotBlank());
    }

    /** Not a failure until launch: final Kinyarwanda copy comes from the product owner (spec 14.2). */
    @Test
    void reportsKinyarwandaPlaceholdersStillAwaitingTranslation() {
        long placeholders = kinyarwanda.values().stream()
                .filter(value -> ((String) value).startsWith(PLACEHOLDER_MARKER))
                .count();
        System.out.printf("i18n: %d of %d Kinyarwanda messages are still %s placeholders%n",
                placeholders, kinyarwanda.size(), PLACEHOLDER_MARKER);
    }

    private static Set<String> arguments(String message) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(message);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static Properties load(String resource) {
        Properties properties = new Properties();
        try (InputStream in = I18nKeyParityTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return properties;
    }
}
