package service;
import db.TruyVanDB;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import util.KetNoiDB;
/** S5-09: cảnh báo trên tồn khả dụng; ngưỡng theo đơn vị cơ sở, phạm vi kho ở server. */
public class CanhBaoTonService {
 public Map<String,Object> list(long user,Collection<String> roles)throws SQLException{
  try(Connection c=KetNoiDB.getConnection()){
   String scope=KhoService.manager(roles)?"":" AND EXISTS(SELECT 1 FROM user_warehouses u WHERE u.warehouse_id=w.id AND u.user_id=?)";
   Object[] args=KhoService.manager(roles)?new Object[0]:new Object[]{user};
   var rows=TruyVanDB.rows(c,"SELECT w.id warehouse_id,w.name warehouse_name,p.id product_id,p.sku,p.name,p.base_unit,t.minimum_quantity,COALESCE(i.on_hand,0) on_hand,COALESCE(i.reserved,0) reserved,COALESCE(i.on_hand-i.reserved,0) available,(COALESCE(i.on_hand-i.reserved,0)<t.minimum_quantity) low FROM stock_thresholds t JOIN warehouses w ON w.id=t.warehouse_id JOIN products p ON p.id=t.product_id LEFT JOIN inventory i ON i.warehouse_id=w.id AND i.product_id=p.id WHERE w.active AND p.active"+scope+" ORDER BY w.code,p.sku",args);
   return Map.of("items",rows,"canManage",KhoService.manager(roles));
  }
 }
 public Map<String,Object> masters(long user,Collection<String> roles)throws SQLException{
  var out=new LinkedHashMap<>(new KhoService().list(user,roles));
  try(Connection c=KetNoiDB.getConnection()){out.put("products",TruyVanDB.rows(c,"SELECT id,sku,name,base_unit FROM products WHERE active ORDER BY sku"));}return out;
 }
 public void save(long wh,long product,long user,Collection<String> roles,Object value)throws SQLException{
  if(!KhoService.manager(roles))throw new SecurityException("Chỉ quản lý kho được cấu hình ngưỡng tồn");
  BigDecimal n;try{n=new BigDecimal(Objects.toString(value,""));}catch(Exception e){throw new IllegalArgumentException("Ngưỡng tồn không hợp lệ");}
  if(n.signum()<0||n.compareTo(new BigDecimal("1000000000"))>0||n.stripTrailingZeros().scale()>0)throw new IllegalArgumentException("Ngưỡng phải là số nguyên từ 0 đến 1 tỷ đơn vị cơ sở");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   KhoService.access(c,wh,user,roles);KhoService.actor(c,user);
   if(TruyVanDB.one(c,"SELECT id FROM products WHERE id=? AND active",product)==null)throw new IllegalArgumentException("Sản phẩm không còn hoạt động");
   TruyVanDB.update(c,"INSERT INTO stock_thresholds(warehouse_id,product_id,minimum_quantity,updated_by) VALUES(?,?,?,?) ON CONFLICT(warehouse_id,product_id) DO UPDATE SET minimum_quantity=EXCLUDED.minimum_quantity,updated_by=EXCLUDED.updated_by,updated_at=now()",wh,product,n,user);c.commit();
  }catch(Exception e){c.rollback();throw e;}}
 }
}
