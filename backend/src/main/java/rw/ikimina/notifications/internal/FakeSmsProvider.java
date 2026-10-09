package rw.ikimina.notifications.internal;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import rw.ikimina.notifications.SmsProvider;

/**
 * Stands in for an SMS gateway in development and tests (spec 13.1/14.1: code against an
 * interface plus a fake). Keeps the most recent messages in memory so tests can read the
 * one-time code a user would have received.
 *
 * <p>Message text is logged only when {@code ikimina.sms.fake.log-messages} is true, which
 * only the dev profile sets - spec 16.6 forbids logging one-time codes anywhere else.
 */
@Component
@ConditionalOnProperty(name = "ikimina.sms.provider", havingValue = "fake")
public class FakeSmsProvider implements SmsProvider {

    public record SentSms(String to, String text) {
    }

    private static final Logger log = LoggerFactory.getLogger(FakeSmsProvider.class);
    private static final int CAPACITY = 1_000;

    private final Deque<SentSms> sent = new ConcurrentLinkedDeque<>();
    private final boolean logMessages;

    public FakeSmsProvider(@Value("${ikimina.sms.fake.log-messages:false}") boolean logMessages) {
        this.logMessages = logMessages;
    }

    @Override
    public void send(String e164, String text) {
        sent.addFirst(new SentSms(e164, text));
        while (sent.size() > CAPACITY) {
            sent.pollLast();
        }
        if (logMessages) {
            log.info("[fake SMS to {}] {}", e164, text);
        }
    }

    /** Newest first. */
    public List<SentSms> sentTo(String e164) {
        List<SentSms> matching = new ArrayList<>();
        for (SentSms sms : sent) {
            if (sms.to().equals(e164)) {
                matching.add(sms);
            }
        }
        return matching;
    }
}
