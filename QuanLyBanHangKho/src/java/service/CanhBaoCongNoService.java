package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.util.*;
public final class CanhBaoCongNoService {
 public Map<String,Object> list()throws SQLException{try(Connection c=KetNoiDB.getConnection()){
  return Map.of("policy",TruyVanDB.one(c,"SELECT * FROM credit_risk_policy WHERE id=1"),"items",TruyVanDB.rows(c,"SELECT c.id,c.code,c.name,s.*,a.evaluated_at FROM customers c JOIN customer_credit_status s ON s.customer_id=c.id LEFT JOIN customer_credit_alerts a ON a.customer_id=c.id WHERE s.outstanding>s.credit_limit OR s.overdue_orders>0 ORDER BY s.overdue_amount DESC,c.code"));
 }}
 public void policy(long actor,Map<String,Object> in)throws SQLException{
  String over=KhoService.text(in.get("overLimitMode"),"Xử lý vượt hạn mức",10,true),late=KhoService.text(in.get("overdueMode"),"Xử lý quá hạn",10,true);
  if(!Set.of("BLOCK","REVIEW").contains(over)||!Set.of("BLOCK","REVIEW").contains(late))throw new IllegalArgumentException("Chọn Chặn hoặc Cần duyệt");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{KhoService.actor(c,actor);TruyVanDB.update(c,"UPDATE credit_risk_policy SET over_limit_mode=?,overdue_mode=? WHERE id=1",over,late);TruyVanDB.rows(c,"SELECT evaluate_customer_credit(id) FROM customers ORDER BY id");c.commit();}catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 public void daily()throws SQLException{try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
  if(TruyVanDB.update(c,"INSERT INTO credit_risk_daily_runs(run_date) VALUES(CURRENT_DATE) ON CONFLICT DO NOTHING")>0)TruyVanDB.rows(c,"SELECT evaluate_customer_credit(id) FROM customers ORDER BY id");c.commit();
 }catch(SQLException|RuntimeException e){c.rollback();throw e;}}}
}
