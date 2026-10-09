package service;

import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.util.logging.Logger;

public final class MailOutboxWorker {
    private static final Logger LOG=Logger.getLogger(MailOutboxWorker.class.getName());
    @FunctionalInterface interface Sender {
        void send(String recipient,String subject,String body,String actionUrl) throws Exception;
    }
    private MailOutboxWorker() {}
    public static void tick() {
        try(Connection c=KetNoiDB.getConnection()) {
            c.setAutoCommit(false);
            processOne(c,SmtpService::send);
            c.commit();
        } catch(Exception e) {
            LOG.warning("Mail queue unavailable: "+e.getClass().getSimpleName());
        }
    }
    static boolean processOne(Connection c,Sender sender) throws SQLException {
        TruyVanDB.update(c,"UPDATE mail_outbox SET status='FAILED',body=NULL,action_url=NULL,last_error='Expired' WHERE status='PENDING' AND expires_at<=CURRENT_TIMESTAMP");
        var row=TruyVanDB.one(c,"SELECT * FROM mail_outbox WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1");
        if(row==null)return false;
        long id=((Number)row.get("id")).longValue();
        try {
            sender.send((String)row.get("recipient"),(String)row.get("subject"),(String)row.get("body"),(String)row.get("action_url"));
            TruyVanDB.update(c,"UPDATE mail_outbox SET status='SENT',attempts=attempts+1,sent_at=CURRENT_TIMESTAMP,body=NULL,action_url=NULL,last_error=NULL WHERE id=?",id);
        } catch(Exception e) {
            int attempts=((Number)row.get("attempts")).intValue()+1;
            TruyVanDB.update(c,"UPDATE mail_outbox SET attempts=?,status=?,next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '1 minute',last_error=?,body=CASE WHEN ? THEN NULL ELSE body END,action_url=CASE WHEN ? THEN NULL ELSE action_url END WHERE id=?",
                attempts,attempts>=5?"FAILED":"PENDING",e.getClass().getSimpleName(),attempts>=5,attempts>=5,id);
            LOG.warning("Mail #"+id+" delivery failed; attempt "+attempts);
        }
        return true;
    }
}
