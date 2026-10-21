package service;
import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;

public final class CongNoDaiLyService {
 public static long customer(Connection c,long user)throws SQLException{
  var row=TruyVanDB.one(c,"SELECT id FROM customers WHERE portal_user_id=?",user);
  if(row==null)throw new SecurityException("Tài khoản chưa được liên kết với đại lý");return TruyVanDB.id(row);
 }
 public Map<String,Object> list(long user)throws SQLException{
  try(Connection c=KetNoiDB.getConnection()){
   long customer=customer(c,user);
   var summary=TruyVanDB.one(c,"SELECT c.code,c.name,c.credit_limit,c.credit_days,COALESCE(b.outstanding,0) total_outstanding,COALESCE(b.overdue,0) overdue_debt,(c.credit_limit-COALESCE(b.outstanding,0)-COALESCE(o.committed,0)) remaining_credit FROM customers c LEFT JOIN LATERAL(SELECT sum(outstanding) outstanding,sum(outstanding) FILTER(WHERE due_date<CURRENT_DATE AND outstanding>0) overdue FROM receivable_invoice_balances WHERE customer_id=c.id) b ON true LEFT JOIN LATERAL(SELECT sum(payable) committed FROM orders o WHERE o.agent_id=c.id AND o.status IN('APPROVED','PICKING','SHIPPED','DELIVERED') AND NOT EXISTS(SELECT 1 FROM invoice_deliveries d WHERE d.order_id=o.id)) o ON true WHERE c.id=?",customer);
   var invoices=TruyVanDB.rows(c,"SELECT id,invoice_number,total_amount,adjustment_amount,paid_amount,outstanding,issued_at::date::text issue_date,due_date::text due_date,CASE WHEN outstanding<=0 THEN 'PAID' WHEN paid_amount>0 THEN 'PARTIAL' WHEN due_date<CURRENT_DATE THEN 'OVERDUE' ELSE 'UNPAID' END status,(due_date<CURRENT_DATE AND outstanding>0) overdue FROM receivable_invoice_balances WHERE customer_id=? ORDER BY issued_at DESC,id DESC",customer);
   return Map.of("summary",summary,"invoices",invoices);
  }
 }
 public Map<String,Object> detail(long user,long invoice)throws SQLException{
  try(Connection c=KetNoiDB.getConnection()){
   long customer=customer(c,user);
   var row=TruyVanDB.one(c,"SELECT id FROM invoices WHERE id=? AND customer_id=?",invoice,customer);
   if(row==null)throw new NoSuchElementException("Không tìm thấy hóa đơn của đại lý");
   return new HoaDonService().detail(invoice);
  }
 }
}
