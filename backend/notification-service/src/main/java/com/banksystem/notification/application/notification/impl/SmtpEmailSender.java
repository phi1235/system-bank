package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.EmailSender;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real SMTP provider. Activated when {@code bank.email.provider=smtp}.
 * Credentials come from Spring Mail env ({@code SPRING_MAIL_*} / {@code bank.email.*}).
 */
@Component
@ConditionalOnProperty(name = "bank.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

  private final JavaMailSender mailSender;
  private final String from;

  public SmtpEmailSender(
      JavaMailSender mailSender,
      @Value("${bank.email.from}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Override
  @Retry(name = "SMTP")
  @CircuitBreaker(name = "SMTP")
  public void send(String to, String subject, String body) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body == null ? "" : body);
    mailSender.send(message);
    log.info("SMTP_EMAIL sent to={} subject={}", to, subject);
  }

  @Override
  @Retry(name = "SMTP")
  @CircuitBreaker(name = "SMTP")
  public void sendWithAttachment(String to, String subject, String body, String filename, byte[] content) {
    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(from);
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body == null ? "" : body);
      helper.addAttachment(filename, new ByteArrayResource(content));
      mailSender.send(message);
    } catch (MessagingException e) {
      throw new IllegalStateException("Could not build receipt email", e);
    }
  }
}
