package student.jia.my_project_spring_boot_261021.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;

    // 注入 JavaMailSender
    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // 發送電子郵件的方法
    public void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            logger.error("Failed to send email. to={}, subject={}", to, subject, exception);
            throw exception;
        }
    }
}