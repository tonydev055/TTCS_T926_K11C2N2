package service;
import db.TruyVanDB;
import util.KetNoiDB;
import security.PhanQuyen;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

public final class ChiTieuDoanhSoService {
 public static boolean manage(Collection<String> roles){return PhanQuyen.allows(roles,"reports.sales");}
 private static void read(Collection<String> roles){if(!manage(roles)&&!PhanQuyen.allows(roles,"targets.own"))throw new SecurityException("Bạn chưa được cấp quyền xem chỉ tiêu");}
 public static YearMonth month(Object raw){try{YearMonth m=YearMonth.parse(Objects.toString(raw,""));if(m.getYear()<2000||m.getYear()>2100)throw new IllegalArgumentException();return m;}catch(RuntimeException e){throw new IllegalArgumentException("Kỳ chỉ tiêu phải theo định dạng YYYY-MM, từ năm 2000 đến 2100");}}
 public Map<String,Object> list(long actor,Collection<String> roles,String period,String staff,String region)throws SQLException{
  read(roles);YearMonth month=month(period==null||period.isBlank()?YearMonth.now().toString():period);String area=KhoService.text(region,"Khu vực",150,false);
  Long staffId=staff==null||staff.isBlank()?null:KhoService.id(staff);if(!manage(roles)&&staffId!=null&&staffId!=actor)throw new SecurityException("Chỉ được xem chỉ tiêu của chính mình");
  try(Connection c=KetNoiDB.getConnection()){
   var params=new ArrayList<Object>(List.of(month.atDay(1)));String scope=" WHERE t.period_month=?";
   if(!manage(roles)){scope+=" AND t.staff_id=?";params.add(actor);}else if(staffId!=null){scope+=" AND t.staff_id=?";params.add(staffId);}
   if(!area.isEmpty()){scope+=" AND t.region=?";params.add(area);}
   var rows=TruyVanDB.rows(c,"SELECT t.id,t.staff_id,u.full_name staff_name,t.region,to_char(t.period_month,'YYYY-MM') period_month,t.target_amount,t.note,t.version,t.updated_at::text updated_at,(t.period_month<date_trunc('month',CURRENT_DATE)::date) locked,COALESCE(a.amount,0) actual_amount "+
    "FROM sales_targets t JOIN users u ON u.id=t.staff_id LEFT JOIN LATERAL(SELECT sum(o.payable) amount FROM orders o JOIN customers k ON k.id=o.agent_id WHERE k.sales_rep_id=t.staff_id AND k.region=t.region AND o.status IN('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') AND o.created_at>=t.period_month AND o.created_at<t.period_month+INTERVAL '1 month') a ON true"+scope+" ORDER BY u.full_name,t.region,t.id LIMIT 5001",params.toArray());
   if(rows.size()>5000)throw new IllegalArgumentException("Có quá 5.000 chỉ tiêu; hãy lọc theo nhân viên hoặc khu vực");
   return Map.of("items",rows,"canManage",manage(roles),"month",month.toString(),"staff",TruyVanDB.rows(c,"SELECT DISTINCT u.id,u.full_name FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.active AND r.code='SALES_REP'"+(manage(roles)?"":" AND u.id=?")+" ORDER BY u.full_name,u.id",manage(roles)?new Object[]{}:new Object[]{actor}),
    "regions",TruyVanDB.rows(c,"SELECT DISTINCT region FROM customers"+(manage(roles)?"":" WHERE sales_rep_id=?")+" ORDER BY region",manage(roles)?new Object[]{}:new Object[]{actor}));
  }
 }
 public long save(Long id,long actor,Collection<String> roles,Map<String,Object> in)throws SQLException{
  if(!manage(roles))throw new SecurityException("Chỉ người có quyền quản lý báo cáo doanh số được giao chỉ tiêu");
  BigDecimal amount=ThanhToanService.money(in.get("targetAmount"));if(amount.signum()<=0)throw new IllegalArgumentException("Chỉ tiêu phải lớn hơn 0");
  String note=KhoService.text(in.get("note"),"Ghi chú",500,false),reason=KhoService.text(in.get("reason"),"Lý do chỉnh sửa",1000,false);
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   KhoService.actor(c,actor);TruyVanDB.one(c,"SELECT set_config('app.target_reason',?,true)",reason);
   if(id==null){
    YearMonth period=month(in.get("month"));if(period.isBefore(YearMonth.now()))throw new IllegalArgumentException("Không tạo mới chỉ tiêu cho kỳ đã qua");
    long staff=KhoService.id(in.get("staffId"));String region=KhoService.text(in.get("region"),"Khu vực",150,true);
    if(TruyVanDB.one(c,"SELECT 1 FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.id=? AND u.active AND r.code='SALES_REP'",staff)==null)throw new IllegalArgumentException("Chọn nhân viên kinh doanh đang hoạt động");
    if(TruyVanDB.one(c,"SELECT 1 FROM customers WHERE region=? LIMIT 1",region)==null)throw new IllegalArgumentException("Khu vực chưa tồn tại trong danh sách đại lý");
    id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO sales_targets(period_month,staff_id,region,target_amount,note,created_by,updated_by) VALUES(?,?,?,?,?,?,?) RETURNING id",period.atDay(1),staff,region,amount,note,actor,actor));
   }else{
    var target=TruyVanDB.one(c,"SELECT period_month,version FROM sales_targets WHERE id=? FOR UPDATE",id);if(target==null)throw new NoSuchElementException("Không tìm thấy chỉ tiêu");
    long version=KhoService.id(in.get("version"));if(((Number)target.get("version")).longValue()!=version)throw new IllegalArgumentException("Chỉ tiêu đã thay đổi; tải lại trước khi chỉnh sửa");
    if(((java.sql.Date)target.get("period_month")).toLocalDate().isBefore(YearMonth.now().atDay(1))&&reason.isBlank())throw new IllegalArgumentException("Kỳ đã khoá; bắt buộc nhập lý do chỉnh sửa");
    TruyVanDB.update(c,"UPDATE sales_targets SET target_amount=?,note=?,updated_by=? WHERE id=?",amount,note,actor,id);
   }
   c.commit();return id;
  }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 public List<Map<String,Object>> history(long id,long actor,Collection<String> roles)throws SQLException{
  read(roles);try(Connection c=KetNoiDB.getConnection()){
   if(TruyVanDB.one(c,"SELECT id FROM sales_targets WHERE id=?"+(manage(roles)?"":" AND staff_id=?"),manage(roles)?new Object[]{id}:new Object[]{id,actor})==null)throw new NoSuchElementException("Không tìm thấy chỉ tiêu trong phạm vi được phép");
   return TruyVanDB.rows(c,"SELECT h.id,h.old_amount,h.new_amount,h.old_note,h.new_note,h.reason,h.created_at::text created_at,u.full_name actor_name FROM sales_target_changes h JOIN users u ON u.id=h.actor_id WHERE h.target_id=? ORDER BY h.id DESC LIMIT 500",id);
  }
 }
}
