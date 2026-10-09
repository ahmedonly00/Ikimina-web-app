package rw.ikimina.notifications;

/**
 * Sends one text message (spec 14.1). The real gateway is [OPEN] - the product owner has
 * not chosen a Rwanda-capable provider - so the only implementation is the fake one.
 * A real provider is added as a new implementation once its documented API is available;
 * nothing about any provider's API is assumed here.
 */
public interface SmsProvider {

    /** @param e164 recipient, e.g. +250788123456 */
    void send(String e164, String text);
}
