package service;
import db.TruyVanDB;import util.KetNoiDB;import util.XuLyJson;import java.sql.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;
public final class DoiChieuCongNoService {
 private Long own(Connection c,long user,boolean dealer)throws SQLException{
  if(!dealer)return null;var row=TruyVanDB.one(c,"SELECT id FROM customers WHERE portal_user_id=?",user);if(row==null)throw new SecurityException("Tài khoản chưa liên kết đại lý");return TruyVanDB.id(row);
 }
 public Map<String,Object> list(long user,boolean dealer)throws SQLException{try(Connection c=KetNoiDB.getConnection()){
  Long own=own(c,user,dealer);var rows=TruyVanDB.rows(c,"SELECT b.id,b.customer_code,b.customer_name,b.period_from::text period_from,b.period_to::text period_to,b.opening_balance,b.debit,b.credit,b.closing_balance,COALESCE(r.status,'PENDING') status FROM debt_reconciliations b LEFT JOIN debt_reconciliation_responses r ON r.reconciliation_id=b.id"+(dealer?" WHERE b.customer_id=?":"")+" ORDER BY b.id DESC LIMIT 500",dealer?new Object[]{own}:new Object[]{});
  return Map.of("items",rows,"customers",dealer?List.of():TruyVanDB.rows(c,"SELECT id,code,name FROM customers ORDER BY code"),"dealer",dealer);
 }}
 public Map<String,Object> detail(long user,boolean dealer,long id)throws SQLException{try(Connection c=KetNoiDB.getConnection()){return detail(c,user,dealer,id);}}
 private Map<String,Object> detail(Connection c,long user,boolean dealer,long id)throws SQLException{
  Long own=own(c,user,dealer);var row=TruyVanDB.one(c,"SELECT b.*,b.period_from::text period_from,b.period_to::text period_to,b.snapshot::text snapshot,COALESCE(r.status,'PENDING') status,r.reason,r.dealer_balance,r.created_at::text responded_at,u.full_name responded_by_name FROM debt_reconciliations b LEFT JOIN debt_reconciliation_responses r ON r.reconciliation_id=b.id LEFT JOIN users u ON u.id=r.responded_by WHERE b.id=?"+(dealer?" AND b.customer_id=?":""),dealer?new Object[]{id,own}:new Object[]{id});
  if(row==null)throw new NoSuchElementException("Không tìm thấy biên bản trong phạm vi được phép");row.put("snapshot",XuLyJson.object((String)row.get("snapshot")));return row;
 }
 public Map<String,Object> create(long actor,Map<String,Object> in)throws SQLException{
  long customer=KhoService.id(in.get("customerId"));LocalDate from=ThanhToanService.date(in.get("from")),to=ThanhToanService.date(in.get("to"));if(to.isBefore(from))throw new IllegalArgumentException("Kỳ kết thúc phải sau hoặc bằng kỳ bắt đầu");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   ThanhToanService.lockCustomer(c,customer);KhoService.actor(c,actor);
   var existing=TruyVanDB.one(c,"SELECT id FROM debt_reconciliations WHERE customer_id=? AND period_from=? AND period_to=?",customer,from,to);
   if(existing!=null){var result=detail(c,actor,false,TruyVanDB.id(existing));c.commit();return result;}
   var party=TruyVanDB.one(c,"SELECT code,name FROM customers WHERE id=?",customer);
   var opening=(BigDecimal)TruyVanDB.one(c,"SELECT COALESCE(sum(amount),0) amount FROM receivable_ledger_events WHERE customer_id=? AND event_date<?",customer,from).get("amount");
   var events=TruyVanDB.rows(c,"SELECT event_date::text event_date,reference,amount,kind FROM receivable_ledger_events WHERE customer_id=? AND event_date BETWEEN ? AND ? ORDER BY event_date,recorded_at,reference",customer,from,to);
   BigDecimal debit=BigDecimal.ZERO,credit=BigDecimal.ZERO;for(var event:events){var amount=(BigDecimal)event.get("amount");if(amount.signum()>0)debit=debit.add(amount);else credit=credit.subtract(amount);}
   long id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO debt_reconciliations(customer_id,period_from,period_to,customer_code,customer_name,opening_balance,debit,credit,closing_balance,snapshot,created_by) VALUES(?,?,?,?,?,?,?,?,?,?::jsonb,?) RETURNING id",customer,from,to,party.get("code"),party.get("name"),opening,debit,credit,opening.add(debit).subtract(credit),XuLyJson.stringify(Map.of("events",events)),actor));
   var result=detail(c,actor,false,id);c.commit();return result;
  }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 public Map<String,Object> respond(long actor,long id,Map<String,Object> in)throws SQLException{
  String status=KhoService.text(in.get("status"),"Kết quả",20,true);if(!Set.of("CONFIRMED","DISPUTED").contains(status))throw new IllegalArgumentException("Kết quả không hợp lệ");
  String reason=KhoService.text(in.get("reason"),"Lý do",2000,status.equals("DISPUTED"));BigDecimal balance=status.equals("DISPUTED")?ThanhToanService.money(in.get("dealerBalance")):null;
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   Long customer=own(c,actor,true);var row=TruyVanDB.one(c,"SELECT id FROM debt_reconciliations WHERE id=? AND customer_id=? FOR UPDATE",id,customer);if(row==null)throw new NoSuchElementException("Không tìm thấy biên bản của đại lý");
   if(TruyVanDB.one(c,"SELECT 1 FROM debt_reconciliation_responses WHERE reconciliation_id=?",id)!=null)throw new IllegalArgumentException("Biên bản đã có phản hồi; liên hệ kế toán để xử lý");
   KhoService.actor(c,actor);TruyVanDB.update(c,"INSERT INTO debt_reconciliation_responses(reconciliation_id,status,reason,dealer_balance,responded_by) VALUES(?,?,?,?,?)",id,status,reason,balance,actor);
   var result=detail(c,actor,true,id);c.commit();return result;
  }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
}
