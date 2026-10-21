package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.util.*;
public final class ReminderIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  if(!ThuDienTuService.developmentMailbox())throw new IllegalStateException("Kiểm thử không được gửi email ra ngoài");
  long actor,other,customer;try(Connection c=KetNoiDB.getConnection()){
   actor=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.accountant'"));other=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.other'"));customer=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM customers WHERE code='DL-S7'"));
   TruyVanDB.update(c,"UPDATE customers SET credit_days=3 WHERE id=?",customer);
   for(String code:List.of("HD-REM-SOON","HD-REM-LATE"))TruyVanDB.update(c,"INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,issued_at) VALUES(?,?,CURRENT_DATE,CURRENT_DATE,100,0,100,?,CASE WHEN ?='HD-REM-LATE' THEN CURRENT_TIMESTAMP-INTERVAL '10 days' ELSE CURRENT_TIMESTAMP END)",code,customer,actor,code);
   TruyVanDB.update(c,"UPDATE customers SET credit_days=30 WHERE id=?",customer);
  }
  var service=new NhacNoService();if(service.daily()!=2)throw new AssertionError("Nhắc đúng trước 3 ngày và quá hạn");if(service.daily()!=0)throw new AssertionError("Nhắc trùng trong ngày");
  var own=(List<Map<String,Object>>)service.list(actor,true).get("items");if(own.size()!=2||own.stream().anyMatch(r->!"DEVELOPMENT_MAILBOX".equals(r.get("email_status"))))throw new AssertionError("Không được báo DEV đã gửi thật");
  if(!((List<?>)service.list(other,true).get("items")).isEmpty())throw new AssertionError("Lộ thông báo");long notification=((Number)own.get(0).get("id")).longValue();
  try{service.read(other,notification);throw new AssertionError("Đánh dấu đọc hộ");}catch(NoSuchElementException expected){}
  service.read(actor,notification);
  try(Connection c=KetNoiDB.getConnection()){
   c.setAutoCommit(false);long id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO mail_outbox(recipient,subject,body,expires_at) VALUES('test@s7.test','Test','Test',CURRENT_TIMESTAMP+INTERVAL '1 hour') RETURNING id"));
   MailOutboxWorker.processOne(c,(a,b,d,u)->{throw new java.io.IOException("Test failure");});
   if(!"PENDING".equals(TruyVanDB.one(c,"SELECT status FROM mail_outbox WHERE id=?",id).get("status")))throw new AssertionError("Gửi lỗi không được báo SENT");
   TruyVanDB.update(c,"UPDATE mail_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",id);MailOutboxWorker.processOne(c,(a,b,d,u)->{});
   if(!"SENT".equals(TruyVanDB.one(c,"SELECT status FROM mail_outbox WHERE id=?",id).get("status")))throw new AssertionError("Chỉ SENT sau sender thành công");c.rollback();
  }
  System.out.println("S7-08: lịch nhắc, chống trùng, phạm vi đại lý và trạng thái mail đạt; không gửi email thật");
 }
}
