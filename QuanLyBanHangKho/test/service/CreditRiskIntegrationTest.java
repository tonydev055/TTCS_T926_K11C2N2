package service;
import db.TruyVanDB;import db.DonHangNhapDB;import util.KetNoiDB;import java.sql.*;import java.util.*;import java.time.LocalDate;
public final class CreditRiskIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  long actor,customer,invoice;try(Connection c=KetNoiDB.getConnection()){
   actor=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.accountant'"));customer=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM customers WHERE code='DL-S7'"));
   invoice=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,issued_at) VALUES('HD-RISK',?,CURRENT_DATE-90,CURRENT_DATE-90,100,0,100,?,CURRENT_TIMESTAMP-INTERVAL '90 days') RETURNING id",customer,actor));
  }
  var risk=new CanhBaoCongNoService();var db=new DonHangNhapDB();risk.policy(actor,Map.of("overLimitMode","REVIEW","overdueMode","BLOCK"));
  if(!Boolean.TRUE.equals(db.creditStatus(customer).get("blocked")))throw new AssertionError("Không chặn nợ quá hạn");
  risk.policy(actor,Map.of("overLimitMode","REVIEW","overdueMode","REVIEW"));if(Boolean.TRUE.equals(db.creditStatus(customer).get("blocked")))throw new AssertionError("REVIEW không được chặn cứng");
  new ThanhToanService().create(actor,Map.of("customerId",customer,"invoiceId",invoice,"amount","100","paymentMethod","CASH","receivedDate",LocalDate.now().toString(),"requestKey",UUID.randomUUID().toString()));
  try(Connection c=KetNoiDB.getConnection()){
   if(Boolean.TRUE.equals(TruyVanDB.one(c,"SELECT overdue FROM customer_credit_alerts WHERE customer_id=?",customer).get("overdue")))throw new AssertionError("Thu tiền phải đánh giá lại ngay");
   TruyVanDB.update(c,"UPDATE customers SET credit_limit=500 WHERE id=?",customer);
  }
  risk.policy(actor,Map.of("overLimitMode","BLOCK","overdueMode","BLOCK"));if(!Boolean.TRUE.equals(db.creditStatus(customer).get("blocked")))throw new AssertionError("Không chặn vượt hạn mức");
  risk.daily();risk.daily();try(Connection c=KetNoiDB.getConnection()){
   if(((Number)TruyVanDB.one(c,"SELECT count(*) n FROM credit_risk_daily_runs WHERE run_date=CURRENT_DATE").get("n")).intValue()!=1)throw new AssertionError("Job chạy lặp trong ngày");
   TruyVanDB.update(c,"UPDATE customers SET credit_limit=100000 WHERE id=?",customer);
  }
  risk.policy(actor,Map.of("overLimitMode","REVIEW","overdueMode","BLOCK"));System.out.println("S7-04: BLOCK/REVIEW, cập nhật sau thu và job một lần/ngày đạt");
 }
}
