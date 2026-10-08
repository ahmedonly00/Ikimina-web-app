package rw.ikimina.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

class MoneyJsonConfigTest {

    record Payment(Money amount) {
    }

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new MoneyJsonConfig().moneyJacksonModule())
            .build();

    @Test
    void writesMoneyAsAString() {
        assertThat(mapper.writeValueAsString(new Payment(Money.ofWholeRwf(150_000))))
                .isEqualTo("{\"amount\":\"150000.00\"}");
    }

    @Test
    void readsMoneyFromAString() {
        assertThat(mapper.readValue("{\"amount\":\"150000.5\"}", Payment.class).amount())
                .isEqualTo(Money.parse("150000.50"));
    }

    @Test
    void rejectsAJsonNumber() {
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":150000}", Payment.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":0.1}", Payment.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void rejectsAStringThatIsNotAValidAmount() {
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":\"1.234\"}", Payment.class))
                .isInstanceOf(MismatchedInputException.class);
    }
}
