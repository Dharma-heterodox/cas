package org.apereo.cas.support.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.apereo.cas.util.io.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is {@link TwillioSmsSender}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
public class TwillioSmsSender implements SmsSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(TwillioSmsSender.class);
    
    public TwillioSmsSender(final String accountId, final String token) {
        Twilio.init(accountId, token);
    }

    @Override
    public void send(final String from, final String to, final String message) {
        try {
            Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(from),
                    message).create();
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);        
        }
    }
}


