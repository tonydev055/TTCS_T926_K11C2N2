package service;
import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public final class DashboardIntegrationTest {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  try(Connection c=KetNoiDB.getConnection()){
   c.setAutoCommit(false);TruyVanDB.update(c,"SET LOCAL session_replication_role='replica'");
   long actor=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('dashboard-test','dashboard@test.local','Kiểm thử','x') RETURNING id"));
   long group=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO customer_groups(code,name) VALUES('DASHBOARD-TEST','Kiểm thử') RETURNING id"));
   long customer=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO customers(code,name,customer_group_id,region) VALUES('DASHBOARD-TEST','Kiểm thử',?,'Bắc') RETURNING id",group));
   TruyVanDB.update(c,"INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,due_date) VALUES('DASHBOARD-TEST',?,CURRENT_DATE-20,CURRENT_DATE,100,0,100,?,CURRENT_DATE-10)",customer,actor);
   c.commit();
  }
  var method=controller.DashboardServlet.class.getDeclaredMethod("overdue");method.setAccessible(true);
  var rows=(List<Map<String,Object>>)method.invoke(new controller.DashboardServlet());
  if(rows.size()!=1||((Number)rows.get(0).get("maxDaysLate")).intValue()!=10||((BigDecimal)rows.get(0).get("overdueAmount")).compareTo(new BigDecimal("100"))!=0)throw new AssertionError("Sai tiền hoặc số ngày quá hạn");
  System.out.println("S8-05: truy vấn công nợ, giá trị và số ngày quá hạn đạt");
 }
}
