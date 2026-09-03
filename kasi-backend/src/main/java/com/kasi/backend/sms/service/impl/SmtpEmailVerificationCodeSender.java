package com.kasi.backend.sms.service.impl;

import com.kasi.backend.auth.service.VerificationCodeSender;
import com.kasi.backend.common.enums.TargetType;
import com.kasi.backend.common.enums.VerificationScene;
import com.kasi.backend.common.exception.VerificationDeliveryUnavailableException;
import com.kasi.backend.sms.entity.SmsConfig;
import com.kasi.backend.sms.mapper.SmsConfigMapper;
import com.kasi.backend.common.crypto.CredentialCipher;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.Properties;

@Profile("!local & !test")
@Service
@RequiredArgsConstructor
public class SmtpEmailVerificationCodeSender implements VerificationCodeSender {
    private final SmsConfigMapper mapper;
    private final CredentialCipher cipher;
    @Override public void send(String target, TargetType type, VerificationScene scene, String code) {
        if (type != TargetType.EMAIL) throw new VerificationDeliveryUnavailableException();
        SmsConfig c = mapper.findSingleton();
        if (c == null || c.getEmailEnabled() != 1 || c.getSmtpHost() == null || c.getSmtpPort() == null || c.getSmtpUsername() == null || c.getSmtpPasswordCiphertext() == null || c.getSmtpFromAddress() == null) throw new VerificationDeliveryUnavailableException();
        try {
            Properties p = new Properties(); p.put("mail.smtp.host", c.getSmtpHost()); p.put("mail.smtp.port", String.valueOf(c.getSmtpPort())); p.put("mail.smtp.auth", "true");
            Session session = Session.getInstance(p);
            MimeMessage message = new MimeMessage(session); message.setFrom(new InternetAddress(c.getSmtpFromAddress())); message.setRecipients(Message.RecipientType.TO, target); message.setSubject("验证码"); message.setText("您的验证码是 " + code + "，5分钟内有效。", "UTF-8");
            Transport transport = session.getTransport("smtp"); transport.connect(c.getSmtpUsername(), cipher.decrypt(c.getSmtpPasswordCiphertext())); transport.sendMessage(message, message.getAllRecipients()); transport.close();
        } catch (Exception e) { throw new VerificationDeliveryUnavailableException(e); }
    }
}
