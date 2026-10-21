package service;
import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.nio.file.*;

public final class SalesTargetsIntegrationTest {
 static long insert(Connection c,String sql,Object... args)throws SQLException{return TruyVanDB.id(TruyVanDB.one(c,sql,args));}
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  long manager,rep,other;
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   s.execute(Files.readString(Path.of("database/sprint8-sales-targets.sql")));c.setAutoCommit(false);s.execute("SET LOCAL session_replication_role='replica'");
   manager=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('target-manager','tm@test.local','Quản lý kiểm thử','x') RETURNING id");
   rep=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('target-rep','tr@test.local','Nhân viên kiểm thử','x') RETURNING id");
   other=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('target-other','to@test.local','Nhân viên khác','x') RETURNING id");
   TruyVanDB.update(c,"INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='SALES_REP'",rep);TruyVanDB.update(c,"INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='SALES_REP'",other);
   long group=insert(c,"INSERT INTO customer_groups(code,name) VALUES('TARGET-TEST','Kiểm thử') RETURNING id");long customer=insert(c,"INSERT INTO customers(code,name,customer_group_id,region,sales_rep_id) VALUES('TARGET-TEST','Kiểm thử',?,'Bắc',?) RETURNING id",group,rep);
   TruyVanDB.update(c,"INSERT INTO orders(code,status,agent_id,created_by,subtotal,discount_total,payable) VALUES('TARGET-TEST','APPROVED',?,?,100,0,100)",customer,rep);c.commit();
  }
  var service=new ChiTieuDoanhSoService();var role=List.of("SALES_MANAGER");String month=YearMonth.now().toString();
  var payload=Map.<String,Object>of("month",month,"staffId",rep,"region","Bắc","targetAmount","1000","note","Chỉ tiêu thật");
  long id=service.save(null,manager,role,payload);
  var data=service.list(rep,List.of("SALES_REP"),month,null,null);var rows=(List<?>)data.get("items");if(rows.size()!=1||((BigDecimal)((Map<?,?>)rows.get(0)).get("actual_amount")).compareTo(new BigDecimal("100"))!=0)throw new AssertionError("Tiến độ phải lấy đơn thật đúng nhân viên/khu vực");
  if(!((List<?>)service.list(other,List.of("SALES_REP"),month,null,null).get("items")).isEmpty())throw new AssertionError("Lộ chỉ tiêu của người khác");
  try{service.save(id,rep,List.of("SALES_REP"),Map.of("targetAmount","2000","version",1));throw new AssertionError("Nhân viên tự sửa");}catch(SecurityException expected){}
  try{service.history(id,other,List.of("SALES_REP"));throw new AssertionError("Lộ lịch sử");}catch(NoSuchElementException expected){}
  try{service.save(null,manager,role,payload);throw new AssertionError("Chỉ tiêu trùng");}catch(SQLException expected){if(!"23505".equals(expected.getSQLState()))throw expected;}
  service.save(id,manager,role,Map.of("targetAmount","1500","version",1,"note","Điều chỉnh"));
  try{service.save(id,manager,role,Map.of("targetAmount","2000","version",1));throw new AssertionError("Ghi đè phiên bản cũ");}catch(IllegalArgumentException expected){}
  long old;
  try(Connection c=KetNoiDB.getConnection()){old=insert(c,"INSERT INTO sales_targets(period_month,staff_id,region,target_amount,note,created_by,updated_by) VALUES(?,?,?,1000,'Kỳ trước',?,?) RETURNING id",YearMonth.now().minusMonths(1).atDay(1),rep,"Bắc",manager,manager);}
  try{service.save(old,manager,role,Map.of("targetAmount","1100","version",1));throw new AssertionError("Kỳ cũ thiếu lý do");}catch(IllegalArgumentException expected){}
  service.save(old,manager,role,Map.of("targetAmount","1100","version",1,"reason","Điều chỉnh được phê duyệt"));
  var history=service.history(old,manager,role);if(history.size()!=2||!history.get(0).get("reason").equals("Điều chỉnh được phê duyệt"))throw new AssertionError("Lịch sử phải lưu thật và đúng lý do");
  try(Connection c=KetNoiDB.getConnection()){try{TruyVanDB.update(c,"UPDATE sales_targets SET target_amount=1200 WHERE id=?",old);throw new AssertionError("SQL sửa kỳ cũ thiếu lý do");}catch(SQLException expected){if(!"P0001".equals(expected.getSQLState()))throw expected;}}
  System.out.println("S8-04: lưu PostgreSQL, tiến độ, phạm vi cá nhân, chặn sửa trái quyền, phiên bản và lịch sử kỳ cũ đạt");
 }
}
