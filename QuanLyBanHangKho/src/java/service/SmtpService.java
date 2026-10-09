package service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.*;

public final class SmtpService {
    private SmtpService() {}
    // No generated toString: never expose the password.
    static final class Settings {
        final String username, password;
        Settings(String username,String password){this.username=username;this.password=password;}
    }
    static Settings settings(Map<String,String> env) {
        String username=env.getOrDefault("SMTP_USERNAME", "").trim();
        String password=env.getOrDefault("SMTP_APP_PASSWORD", "").replace(" ", "");
        if(!username.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||password.isBlank())
            throw new IllegalStateException("Chưa cấu hình SMTP_USERNAME và SMTP_APP_PASSWORD cho Gmail");
        return new Settings(username,password);
    }
    static Properties properties() {
        Properties p=new Properties();
        p.setProperty("mail.smtp.host","smtp.gmail.com");
        p.setProperty("mail.smtp.port","587");
        p.setProperty("mail.smtp.auth","true");
        p.setProperty("mail.smtp.starttls.enable","true");
        p.setProperty("mail.smtp.starttls.required","true");
        p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","10000");
        p.setProperty("mail.smtp.timeout","15000");
        p.setProperty("mail.smtp.writetimeout","15000");
        return p;
    }
    static MimeMessage message(Session session,String from,String recipient,String subject,String body,String actionUrl) throws MessagingException {
        InternetAddress to=new InternetAddress(recipient,true);to.validate();
        MimeMessage message=new MimeMessage(session);
        message.setFrom(new InternetAddress(from,true));
        message.setRecipient(Message.RecipientType.TO,to);
        message.setSubject(subject,"UTF-8");
        message.setText(body+(actionUrl==null?"":"\n\n"+actionUrl),"UTF-8");
        message.setSentDate(new Date());message.saveChanges();return message;
    }
    public static void send(String recipient,String subject,String body,String actionUrl) throws MessagingException {
        Settings config=settings(System.getenv());
        Session session=Session.getInstance(properties());
        MimeMessage message=message(session,config.username,recipient,subject,body,actionUrl);
        try(Transport transport=session.getTransport("smtp")) {
            transport.connect("smtp.gmail.com",587,config.username,config.password);
            transport.sendMessage(message,message.getAllRecipients());
        }
    }
}
