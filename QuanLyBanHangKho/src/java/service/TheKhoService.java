package service;
import db.*;import util.KetNoiDB;import java.sql.*;import java.time.*;import java.util.*;
/** S6-02: số dư tính từ tồn hiện tại trừ biến động về sau, giữ cả tồn trước khi dùng sổ. */
public class TheKhoService {
 public Map<String,Object> masters(long user,Collection<String> roles)throws SQLException{var out=new LinkedHashMap<String,Object>(new KhoService().list(user,roles));try(Connection c=KetNoiDB.getConnection()){out.put("products",TruyVanDB.rows(c,"SELECT id,sku,name FROM products ORDER BY sku"));}return out;}
 public Map<String,Object> list(long user,Collection<String> roles,long wh,long product,String from,String to,int page)throws SQLException{
  LocalDate a,b;try{a=from==null||from.isBlank()?LocalDate.now().minusMonths(1):LocalDate.parse(from);b=to==null||to.isBlank()?LocalDate.now():LocalDate.parse(to);}catch(Exception e){throw new IllegalArgumentException("Ngày lọc không hợp lệ");}if(a.isAfter(b)||page<1||page>100000)throw new IllegalArgumentException("Khoảng ngày hoặc trang không hợp lệ");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);KhoService.access(c,wh,user,roles);
   var p=TruyVanDB.one(c,"SELECT id,sku,name,base_unit FROM products WHERE id=?",product);if(p==null)throw new NoSuchElementException("Không tìm thấy sản phẩm");
   String balance="SELECT m.*,COALESCE(i.on_hand,0)-COALESCE(SUM(m.delta) OVER(ORDER BY m.created_at DESC,m.id DESC ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING),0) balance FROM stock_movements m LEFT JOIN inventory i ON i.warehouse_id=m.warehouse_id AND i.product_id=m.product_id WHERE m.warehouse_id=? AND m.product_id=?";
   var rows=TruyVanDB.rows(c,"SELECT s.*,to_char(s.created_at,'YYYY-MM-DD HH24:MI:SS') occurred_at FROM ("+balance+") s WHERE s.created_at>=? AND s.created_at<? ORDER BY s.created_at,s.id LIMIT 50 OFFSET ?",wh,product,Timestamp.valueOf(a.atStartOfDay()),Timestamp.valueOf(b.plusDays(1).atStartOfDay()),(page-1)*50);
   var total=TruyVanDB.one(c,"SELECT count(*) n FROM stock_movements WHERE warehouse_id=? AND product_id=? AND created_at>=? AND created_at<?",wh,product,Timestamp.valueOf(a.atStartOfDay()),Timestamp.valueOf(b.plusDays(1).atStartOfDay()));
   for(var row:rows){row.remove("created_at");row.put("documentCode",code(c,row));}
   var opening=TruyVanDB.one(c,"SELECT COALESCE((SELECT on_hand FROM inventory WHERE warehouse_id=? AND product_id=?),0)-COALESCE(SUM(delta),0) qty FROM stock_movements WHERE warehouse_id=? AND product_id=? AND created_at>=?",wh,product,wh,product,Timestamp.valueOf(a.atStartOfDay()));
   c.commit();return Map.of("product",p,"items",rows,"total",total.get("n"),"page",page,"openingBalance",opening.get("qty"));
  }
 }
 private String code(Connection c,Map<String,Object> m)throws SQLException{String k=(String)m.get("kind"),table=k.equals("RECEIPT")?"stock_receipts":k.startsWith("TRANSFER_")?"stock_transfers":k.equals("COUNT")?"stock_counts":Set.of("SHIPMENT_OUT","DELIVERY_RETURN").contains(k)?"orders":k.equals("SALES_RETURN")?"sales_returns":null;if(table==null)return String.valueOf(m.get("document_id"));var h=TruyVanDB.one(c,"SELECT code FROM "+table+" WHERE id=?",m.get("document_id"));return h==null?String.valueOf(m.get("document_id")):String.valueOf(h.get("code"));}
 public Map<String,Object> document(long movement,long user,Collection<String> roles)throws SQLException{try(Connection c=KetNoiDB.getConnection()){var m=TruyVanDB.one(c,"SELECT * FROM stock_movements WHERE id=?",movement);if(m==null)throw new NoSuchElementException("Không tìm thấy chứng từ");KhoService.access(c,((Number)m.get("warehouse_id")).longValue(),user,roles);return Map.of("code",code(c,m),"kind",m.get("kind"),"items",TruyVanDB.rows(c,"SELECT p.sku,p.name,b.lot_number,m.delta,m.reason,to_char(m.created_at,'YYYY-MM-DD HH24:MI:SS') occurred_at FROM stock_movements m JOIN products p ON p.id=m.product_id LEFT JOIN stock_bins b ON b.id=m.bin_id WHERE m.warehouse_id=? AND m.kind=? AND m.document_id=? ORDER BY m.id",m.get("warehouse_id"),m.get("kind"),m.get("document_id")));}}
}

