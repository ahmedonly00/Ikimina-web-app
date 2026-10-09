package rw.ikimina.notifications;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import rw.ikimina.shared.phone.PhoneNumber;

/**
 * Sends templated SMS (template text lives in the i18n bundle under {@code sms.<key>},
 * EN and RW - Hard Rule H10).
 *
 * <p>Inside a transaction the message is sent only after commit, so a rolled-back
 * registration never texts a code for an account that does not exist. Phase 1 sends
 * directly after commit; Phase 6 moves delivery to the outbox worker with retries
 * (spec 14.1). Message text is never logged - it may hold a one-time code.
 */
@Component
public class SmsNotifier {

    private static final Logger log = LoggerFactory.getLogger(SmsNotifier.class);

    private final SmsProvider provider;
    private final MessageSource messages;

    public SmsNotifier(ObjectProvider<SmsProvider> provider, MessageSource messages) {
        this.provider = provider.getIfAvailable();
        if (this.provider == null) {
            throw new IllegalStateException("No SMS provider configured. Set IKIMINA_SMS_PROVIDER; only 'fake' exists "
                    + "until the product owner chooses a gateway (spec 14.1 [OPEN]).");
        }
        this.messages = messages;
    }

    public void send(PhoneNumber to, Locale locale, String templateKey, Object... args) {
        String text = messages.getMessage("sms." + templateKey, args, locale);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliver(to, templateKey, text);
                }
            });
        } else {
            deliver(to, templateKey, text);
        }
    }

    private void deliver(PhoneNumber to, String templateKey, String text) {
        try {
            provider.send(to.e164(), text);
        } catch (RuntimeException e) {
            // Phase 1 has no retry queue: the user can ask for the message again. Phase 6 adds retries.
            log.error("SMS '{}' to {} failed: {}", templateKey, to.masked(), e.getClass().getSimpleName());
        }
    }
}
