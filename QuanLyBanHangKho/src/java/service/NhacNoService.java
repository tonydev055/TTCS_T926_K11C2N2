package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.util.*;
public final class NhacNoService {
 public int daily()throws SQLException{
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   if(TruyVanDB.update(c,"INSERT INTO payment_reminder_runs(run_date) VALUES(CURRENT_DATE) ON CONFLICT DO NOTHING")==0){c.commit();return 0;}
   int count=0;var candidates=TruyVanDB.rows(c,"SELECT id,customer_id FROM receivable_invoice_balances WHERE outstanding>0 AND(due_date=CURRENT_DATE+3 OR due_date<CURRENT_DATE) ORDER BY customer_id,id");
   for(var candidate:candidates){
    ThanhToanService.lockCustomer(c,((Number)candidate.get("customer_id")).longValue());
    var row=TruyVanDB.one(c,"SELECT b.*,b.due_date::text due_date,CASE WHEN b.due_date<CURRENT_DATE THEN 'OVERDUE' ELSE 'DUE_IN_3_DAYS' END kind,c.name customer_name,c.portal_user_id,u.email FROM receivable_invoice_balances b JOIN customers c ON c.id=b.customer_id LEFT JOIN users u ON u.id=c.portal_user_id AND u.active WHERE b.id=? AND b.outstanding>0 AND(b.due_date=CURRENT_DATE+3 OR b.due_date<CURRENT_DATE)",candidate.get("id"));if(row==null)continue;
    String kind=String.valueOf(row.get("kind"));
    String message="Hóa đơn "+row.get("invoice_number")+" còn nợ "+row.get("outstanding")+" đ, hạn thanh toán "+row.get("due_date")+". "+(kind.equals("OVERDUE")?"Khoản nợ đã quá hạn.":"Còn 3 ngày đến hạn.");
    String email=Objects.toString(row.get("email"),"");String mode="MISSING_RECIPIENT";Object outbox=null,dev=null;
    if(!email.isBlank()){
     var queued=ThuDienTuService.enqueueTracked(c,email,"KhoFlow - Nhắc thanh toán "+row.get("invoice_number"),message,null,System.getenv());mode=(String)queued.get("mode");if(mode.equals("SMTP")){outbox=queued.get("id");TruyVanDB.update(c,"UPDATE mail_outbox SET expires_at=CURRENT_TIMESTAMP+INTERVAL '24 hours' WHERE id=?",outbox);}else dev=queued.get("id");
    }
    TruyVanDB.update(c,"INSERT INTO payment_reminders(invoice_id,customer_id,recipient_user_id,reminder_date,kind,amount,message,mail_mode,mail_outbox_id,dev_mailbox_id) VALUES(?,?,?,CURRENT_DATE,?,?,?,?,?,?)",row.get("id"),row.get("customer_id"),row.get("portal_user_id"),kind,row.get("outstanding"),message,mode,outbox,dev);count++;
   }c.commit();return count;
  }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 public Map<String,Object> list(long user,boolean dealer)throws SQLException{
  try(Connection c=KetNoiDB.getConnection()){
   String scope=dealer?" WHERE r.recipient_user_id=? AND EXISTS(SELECT 1 FROM customers c WHERE c.id=r.customer_id AND c.portal_user_id=?)":"";
   var rows=TruyVanDB.rows(c,"SELECT r.id,r.invoice_id,i.invoice_number,r.reminder_date::text reminder_date,r.kind,r.amount,r.message,r.read_at::text read_at,c.code customer_code,c.name customer_name,CASE WHEN r.mail_mode='DEV' THEN 'DEVELOPMENT_MAILBOX' WHEN r.mail_mode='MISSING_RECIPIENT' THEN 'MISSING_RECIPIENT' ELSE COALESCE(m.status,'FAILED') END email_status,m.last_error,m.sent_at::text sent_at FROM payment_reminders r JOIN invoices i ON i.id=r.invoice_id JOIN customers c ON c.id=r.customer_id LEFT JOIN mail_outbox m ON m.id=r.mail_outbox_id"+scope+" ORDER BY r.id DESC LIMIT 500",dealer?new Object[]{user,user}:new Object[]{});
   return Map.of("items",rows,"dealer",dealer);
  }
 }
 public void read(long user,long id)throws SQLException{try(Connection c=KetNoiDB.getConnection()){
  if(TruyVanDB.update(c,"UPDATE payment_reminders r SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE r.id=? AND r.recipient_user_id=? AND EXISTS(SELECT 1 FROM customers c WHERE c.id=r.customer_id AND c.portal_user_id=?)",id,user,user)==0)throw new NoSuchElementException("Không tìm thấy thông báo của đại lý");
 }}
}
