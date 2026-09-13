package com.repomatch.notify;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.repomatch.config.AppProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Sends alert and reminder emails when SMTP is configured; otherwise everything still shows up in the app.
 *
 * <p>Email is a convenience, never the only copy: callers mark a match as emailed only when this returns true.
 */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final AppProperties.Notify config;

    public Mailer(ObjectProvider<JavaMailSender> sender, AppProperties props) {
        this.sender = sender;
        this.config = props.notifications();
    }

    public boolean enabled() {
        return config.mailFrom() != null && !config.mailFrom().isBlank() && sender.getIfAvailable() != null;
    }

    public boolean send(String to, String subject, String html, String text) {
        if (!enabled() || to == null || to.isBlank()) {
            return false;
        }
        try {
            JavaMailSender mail = sender.getObject();
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(config.mailFrom(), "RepoMatch");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            mail.send(message);
            return true;
        } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
            // Log without the address: it's personal data and the log is shared.
            log.warn("email '{}' not sent: {}", subject, e.getMessage());
            return false;
        }
    }

    /** Links in emails point at the deployed frontend. */
    public String link(String path) {
        String base = config.appBaseUrl() == null ? "" : config.appBaseUrl().replaceAll("/+$", "");
        return base + path;
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
