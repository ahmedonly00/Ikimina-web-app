package rw.ikimina.shared.money;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Money crosses the API as a JSON string, never a number (spec 17), so no client
 * ever parses an amount through a binary float. A JSON number is rejected rather
 * than coerced: accepting {@code 0.1} would invite exactly the float handling the
 * rule exists to prevent.
 */
@Configuration(proxyBeanMethods = false)
public class MoneyJsonConfig {

    @Bean
    public JacksonModule moneyJacksonModule() {
        return new SimpleModule("ikimina-money")
                .addSerializer(Money.class, new MoneySerializer())
                .addDeserializer(Money.class, new MoneyDeserializer());
    }

    static final class MoneySerializer extends StdSerializer<Money> {

        MoneySerializer() {
            super(Money.class);
        }

        @Override
        public void serialize(Money value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.toWireString());
        }
    }

    static final class MoneyDeserializer extends StdDeserializer<Money> {

        MoneyDeserializer() {
            super(Money.class);
        }

        @Override
        public Money deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return (Money) context.handleUnexpectedToken(Money.class, parser);
            }
            String text = parser.getString();
            try {
                return Money.parse(text);
            } catch (MoneyFormatException e) {
                throw context.weirdStringException(text, Money.class, e.getMessage());
            }
        }
    }
}
