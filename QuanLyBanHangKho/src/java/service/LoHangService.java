package service;
import db.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import util.KetNoiDB;
/** S6-01: thông tin lô và ngưỡng cảnh báo theo SKU, trong phạm vi kho được phân công. */
public class LoHangService {
 public Map<String,Object> list(long user,Collection<String> roles,String warehouse,String state)throws SQLException{return list(user,roles,warehouse,state,false);}
 /** includeEmpty=false ẩn lô đã xuất hết (tồn 0) để danh sách chỉ còn hàng thực có. */
 public Map<String,Object> list(long user,Collection<String> roles,String warehouse,String state,boolean includeEmpty)throws SQLException{
  if(!Set.of("","EXPIRED","SOON","VALID").contains(Objects.toString(state,"")))throw new IllegalArgumentException("Bộ lọc hạn dùng không hợp lệ");
  try(Connection c=KetNoiDB.getConnection()){
   String where=" WHERE true";var args=new ArrayList<Object>();
   if(!KhoService.manager(roles)){where+=" AND EXISTS(SELECT 1 FROM user_warehouses uw WHERE uw.user_id=? AND uw.warehouse_id=b.warehouse_id)";args.add(user);}
   if(warehouse!=null&&!warehouse.isBlank()){long wh=KhoService.id(warehouse);KhoService.access(c,wh,user,roles);where+=" AND b.warehouse_id=?";args.add(wh);}
   if("EXPIRED".equals(state))where+=" AND b.expiry_date<CURRENT_DATE AND b.on_hand>0";
   if("SOON".equals(state))where+=" AND b.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE+p.expiry_alert_days AND b.on_hand>0";
   if("VALID".equals(state))where+=" AND (b.expiry_date IS NULL OR b.expiry_date>CURRENT_DATE+p.expiry_alert_days)";
   if(!includeEmpty)where+=" AND b.on_hand>0";
   var items=TruyVanDB.rows(c,"SELECT b.id,b.warehouse_id,b.product_id,p.sku,p.name,p.lot_managed,p.expiry_alert_days,w.name warehouse_name,COALESCE(l.name,'Chưa phân vị trí') location_name,b.lot_number,b.manufactured_date::text manufactured_date,b.expiry_date::text expiry_date,b.on_hand,CASE WHEN b.expiry_date<CURRENT_DATE THEN 'EXPIRED' WHEN b.expiry_date<=CURRENT_DATE+p.expiry_alert_days THEN 'SOON' ELSE 'VALID' END expiry_state FROM stock_bins b JOIN products p ON p.id=b.product_id JOIN warehouses w ON w.id=b.warehouse_id LEFT JOIN warehouse_locations l ON l.id=b.location_id"+where+" ORDER BY b.expiry_date NULLS LAST,p.sku,b.id LIMIT 1000",args.toArray());
   return Map.of("items",items,"warehouses",new KhoService().list(user,roles).get("warehouses"),"canManage",KhoService.manager(roles),"products",TruyVanDB.rows(c,"SELECT id,sku,name,lot_managed,expiry_alert_days FROM products WHERE active ORDER BY sku"));
  }
 }
 public void settings(long product,long user,Collection<String> roles,Map<String,Object> input)throws SQLException{
  if(!KhoService.manager(roles))throw new SecurityException("Chỉ quản lý kho được đổi chế độ và ngưỡng hạn dùng");
  boolean managed=KhoService.active(input.get("lotManaged"));int days;try{days=new java.math.BigDecimal(String.valueOf(input.get("alertDays"))).intValueExact();}catch(Exception e){throw new IllegalArgumentException("Ngưỡng cảnh báo phải là số ngày nguyên");}if(days<0||days>3650)throw new IllegalArgumentException("Ngưỡng cảnh báo từ 0 đến 3650 ngày");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{KhoService.actor(c,user);if(TruyVanDB.update(c,"UPDATE products SET lot_managed=?,expiry_alert_days=? WHERE id=?",managed,days,product)==0)throw new NoSuchElementException("Không tìm thấy sản phẩm");c.commit();}catch(Exception e){c.rollback();throw e;}}
 }
 public void manufacture(long bin,long user,Collection<String> roles,Map<String,Object> input)throws SQLException{
  LocalDate date;try{date=LocalDate.parse(KhoService.text(input.get("manufacturedDate"),"Ngày sản xuất",10,true));}catch(Exception e){throw new IllegalArgumentException("Ngày sản xuất không hợp lệ");}if(date.isAfter(LocalDate.now()))throw new IllegalArgumentException("Ngày sản xuất không được ở tương lai");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{var b=TruyVanDB.one(c,"SELECT warehouse_id,expiry_date FROM stock_bins WHERE id=? FOR UPDATE",bin);if(b==null)throw new NoSuchElementException("Không tìm thấy lô");KhoService.access(c,((Number)b.get("warehouse_id")).longValue(),user,roles);if(b.get("expiry_date")!=null&&date.isAfter(((java.sql.Date)b.get("expiry_date")).toLocalDate()))throw new IllegalArgumentException("Ngày sản xuất phải trước hạn sử dụng");KhoService.actor(c,user);TruyVanDB.update(c,"UPDATE stock_bins SET manufactured_date=? WHERE id=?",java.sql.Date.valueOf(date),bin);c.commit();}catch(Exception e){c.rollback();throw e;}}
 }
}
