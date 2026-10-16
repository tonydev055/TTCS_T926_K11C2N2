package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.util.*;
public final class DealerDebtIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  long actor,invoice,other;try(Connection c=KetNoiDB.getConnection()){
   actor=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.accountant'"));invoice=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM invoices ORDER BY id LIMIT 1"));
   TruyVanDB.update(c,"INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='CUSTOMER' ON CONFLICT DO NOTHING",actor);
   TruyVanDB.update(c,"UPDATE customers SET portal_user_id=? WHERE code='DL-S7'",actor);
   other=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('s7.other','other@s7.test','Đại lý khác','x') RETURNING id"));
   TruyVanDB.update(c,"INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='CUSTOMER'",other);
   TruyVanDB.update(c,"INSERT INTO customers(code,name,customer_group_id,region,phone,portal_user_id) SELECT 'DL-OTHER','Đại lý khác',customer_group_id,'HN','0900000001',? FROM customers WHERE code='DL-S7'",other);
  }
  var service=new CongNoDaiLyService();if(((List<?>)service.list(actor).get("invoices")).size()!=2)throw new AssertionError("Thiếu hóa đơn của chính mình");
  if(!((List<?>)service.list(other).get("invoices")).isEmpty())throw new AssertionError("Lộ hóa đơn đại lý khác");
  service.detail(actor,invoice);
  try{service.detail(other,invoice);throw new AssertionError("IDOR hóa đơn");}catch(NoSuchElementException expected){}
  try{service.list(Long.MAX_VALUE);throw new AssertionError("Tài khoản chưa liên kết");}catch(SecurityException expected){}
  System.out.println("S7-07: phạm vi đại lý, hóa đơn và tài khoản chưa liên kết đều đạt");
 }
}
