package service;

import db.TruyVanDB;
import util.KetNoiDB;
import jakarta.mail.*;
import java.sql.*;
import java.util.*;

/** No external mail is sent. Queue fixtures live in a temporary table and roll back. */
public class SmtpSelfTest {
    private static int checks;
    private static void check(boolean value,String label) {
        if(!value)throw new AssertionError(label);checks++;
    }
    public static void main(String[] args) throws Exception {
        var config=Map.of("MAIL_MODE","smtp","SMTP_USERNAME","qa@example.test","SMTP_APP_PASSWORD","abcd efgh ijkl mnop");
        check(ThuDienTuService.mode(Map.of()).equals("dev"),"Explicit development compatibility");
        check(ThuDienTuService.mode(config).equals("smtp"),"SMTP routing");
        try{ThuDienTuService.mode(Map.of("MAIL_MODE","typo"));throw new AssertionError("Invalid mode accepted");}
        catch(IllegalArgumentException expected){checks++;}
        try{SmtpService.settings(Map.of());throw new AssertionError("Missing credentials accepted");}
        catch(IllegalStateException expected){checks++;}
        check(SmtpService.settings(config).password.equals("abcdefghijklmnop"),"App password spacing");
        var properties=SmtpService.properties();
        check(properties.getProperty("mail.smtp.starttls.required").equals("true"),"TLS mandatory");
        check(properties.getProperty("mail.smtp.ssl.checkserveridentity").equals("true"),"Verify TLS hostname");
        check(properties.getProperty("mail.smtp.port").equals("587"),"Gmail submission port");
        var message=SmtpService.message(Session.getInstance(properties),"qa@example.test","recipient@example.test","Đặt lại mật khẩu","Nội dung tiếng Việt","https://example.test/index.html?resetToken=qa");
        check(message.getSubject().equals("Đặt lại mật khẩu"),"Unicode subject");
        check(message.getContent().toString().contains("Nội dung tiếng Việt")&&message.getContent().toString().contains("resetToken=qa"),"Unicode body and reset link");
        try(Connection c=KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            try {
                TruyVanDB.update(c,"CREATE TEMP TABLE mail_outbox (LIKE public.mail_outbox INCLUDING DEFAULTS) ON COMMIT DROP");
                var savepoint=c.setSavepoint();
                ThuDienTuService.enqueue(c,"qa@example.test","QA","secret","https://example.test",config);
                check(TruyVanDB.rows(c,"SELECT * FROM mail_outbox").size()==1,"SMTP queues mail");
                c.rollback(savepoint);
                check(TruyVanDB.rows(c,"SELECT * FROM mail_outbox").isEmpty(),"Rollback removes mail with account transaction");
                ThuDienTuService.enqueue(c,"qa@example.test","QA","secret","https://example.test",config);
                check(MailOutboxWorker.processOne(c,(to,subject,body,url)->check(body.equals("secret"),"Sender receives queued body")),"Worker picks pending mail");
                var row=TruyVanDB.one(c,"SELECT * FROM mail_outbox");
                check(row.get("status").equals("SENT")&&row.get("body")==null&&row.get("action_url")==null,"Sent payload removed");
                check(!MailOutboxWorker.processOne(c,(a,b,d,e)->{throw new AssertionError("Duplicate send");}),"Sent mail not sent again");
                ThuDienTuService.enqueue(c,"qa@example.test","QA failure","secret",null,config);
                for(int n=1;n<=5;n++) {
                    TruyVanDB.update(c,"UPDATE mail_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE status='PENDING'");
                    MailOutboxWorker.processOne(c,(a,b,d,e)->{throw new MessagingException("Must not persist secret response");});
                    row=TruyVanDB.one(c,"SELECT * FROM mail_outbox WHERE subject='QA failure'");
                    check(((Number)row.get("attempts")).intValue()==n,"Retry attempt "+n);
                    check(row.get("status").equals(n==5?"FAILED":"PENDING"),"Retry state "+n);
                }
                check(row.get("body")==null&&row.get("last_error").equals("MessagingException"),"Failure sanitized and payload removed");
                ThuDienTuService.enqueue(c,"qa@example.test","QA expired","secret",null,config);
                TruyVanDB.update(c,"UPDATE mail_outbox SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE subject='QA expired'");
                check(!MailOutboxWorker.processOne(c,(a,b,d,e)->{throw new AssertionError("Expired mail sent");}),"Expired mail not sent");
            } finally {c.rollback();}
        }
        System.out.println("SMTP: "+checks+" checks passed; no external email sent.");
    }
}
