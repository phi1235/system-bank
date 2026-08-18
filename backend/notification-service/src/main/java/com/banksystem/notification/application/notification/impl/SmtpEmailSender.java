package com.banksystem.notification.application.notification.impl;
import com.banksystem.notification.application.notification.EmailSender;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
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
}
