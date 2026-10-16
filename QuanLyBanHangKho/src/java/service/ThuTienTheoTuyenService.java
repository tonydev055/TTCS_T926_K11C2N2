package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.math.BigDecimal;import java.time.LocalDate;import java.util.*;import java.io.IOException;
public final class ThuTienTheoTuyenService {
 public Map<String,Object> list(long actor,boolean accountant)throws SQLException{try(Connection c=KetNoiDB.getConnection()){
  String scope=accountant?"":" WHERE r.collected_by=?";Object[] args=accountant?new Object[]{}:new Object[]{actor};
  var rows=TruyVanDB.rows(c,"SELECT r.id,r.customer_id,c.code customer_code,c.name customer_name,r.amount,r.collected_date::text collected_date,r.note,r.status,r.payment_id,r.received_at::text received_at,u.full_name collector_name,a.full_name accountant_name FROM route_collections r JOIN customers c ON c.id=r.customer_id JOIN users u ON u.id=r.collected_by LEFT JOIN users a ON a.id=r.received_by"+scope+" ORDER BY r.id DESC LIMIT 1000",args);
  var totals=TruyVanDB.rows(c,"SELECT r.collected_by,u.full_name,r.collected_date::text collected_date,sum(r.amount) collected,COALESCE(sum(r.amount) FILTER(WHERE r.status='RECEIVED'),0) submitted,COALESCE(sum(r.amount) FILTER(WHERE r.status='PENDING'),0) pending FROM route_collections r JOIN users u ON u.id=r.collected_by"+scope+" GROUP BY r.collected_by,u.full_name,r.collected_date ORDER BY r.collected_date DESC,u.full_name",args);
  return Map.of("items",rows,"totals",totals,"accountant",accountant,"customers",accountant?List.of():TruyVanDB.rows(c,"SELECT id,code,name FROM customers WHERE sales_rep_id=? AND status='ACTIVE' ORDER BY code",actor));
 }}
 public long create(long actor,Map<String,Object> in,byte[] proof)throws SQLException,IOException{
  long customer=KhoService.id(in.get("customerId"));BigDecimal amount=ThanhToanService.money(in.get("amount"));if(amount.signum()<=0)throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
  LocalDate date=ThanhToanService.date(in.get("collectedDate"));String note=KhoService.text(in.get("note"),"Ghi chú",1000,false);UUID key;try{key=UUID.fromString(Objects.toString(in.get("requestKey"),""));}catch(RuntimeException e){throw new IllegalArgumentException("Mã yêu cầu không hợp lệ");}
  if(proof==null)throw new IllegalArgumentException("Bắt buộc ảnh biên lai JPG/PNG tối đa 2 MB");byte[] image=HinhAnhService.normalize(proof,false).image();if(image.length>2097152)throw new IllegalArgumentException("Ảnh biên lai sau chuẩn hóa quá lớn");
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   ThanhToanService.lockCustomer(c,customer);var owner=TruyVanDB.one(c,"SELECT sales_rep_id FROM customers WHERE id=?",customer);if(owner.get("sales_rep_id")==null||((Number)owner.get("sales_rep_id")).longValue()!=actor)throw new SecurityException("Chỉ thu tiền đại lý được phân công");
   var existing=TruyVanDB.one(c,"SELECT id,collected_by,customer_id,amount,collected_date,note,proof_image FROM route_collections WHERE request_key=?",key);
   if(existing!=null){if(((Number)existing.get("collected_by")).longValue()!=actor||((Number)existing.get("customer_id")).longValue()!=customer||((BigDecimal)existing.get("amount")).compareTo(amount)!=0||!date.equals(((java.sql.Date)existing.get("collected_date")).toLocalDate())||!note.equals(existing.get("note"))||!Arrays.equals(image,(byte[])existing.get("proof_image")))throw new IllegalArgumentException("Mã yêu cầu đã dùng cho khoản thu khác");c.commit();return TruyVanDB.id(existing);}
   var debt=(BigDecimal)TruyVanDB.one(c,"SELECT COALESCE((SELECT sum(outstanding) FROM receivable_invoice_balances WHERE customer_id=? AND issued_at::date<=?),0)-COALESCE((SELECT sum(amount) FROM route_collections WHERE customer_id=? AND status='PENDING'),0) amount",customer,date,customer).get("amount");if(amount.compareTo(debt)>0)throw new IllegalArgumentException("Số tiền vượt công nợ còn lại sau các khoản đang chờ nộp");
   KhoService.actor(c,actor);long id=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO route_collections(customer_id,amount,collected_date,collected_by,note,proof_image,request_key) VALUES(?,?,?,?,?,?,?) RETURNING id",customer,amount,date,actor,note,image,key));c.commit();return id;
  }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 public Map<String,Object> accept(long actor,long id)throws SQLException{
  try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
   var head=TruyVanDB.one(c,"SELECT customer_id FROM route_collections WHERE id=?",id);if(head==null)throw new NoSuchElementException("Không tìm thấy khoản thu");ThanhToanService.lockCustomer(c,((Number)head.get("customer_id")).longValue());
   var row=TruyVanDB.one(c,"SELECT * FROM route_collections WHERE id=? FOR UPDATE",id);if("RECEIVED".equals(row.get("status"))){var result=ThanhToanService.detail(c,((Number)row.get("payment_id")).longValue());c.commit();return result;}
   var payment=new ThanhToanService().create(c,((Number)row.get("collected_by")).longValue(),Map.of("customerId",row.get("customer_id"),"amount",row.get("amount"),"paymentMethod","CASH","receivedDate",row.get("collected_date").toString(),"note","Thu theo tuyến #"+id+": "+row.get("note"),"requestKey",UUID.nameUUIDFromBytes(("route-collection:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()));
   KhoService.actor(c,actor);TruyVanDB.update(c,"UPDATE route_collections SET status='RECEIVED',payment_id=?,received_by=?,received_at=CURRENT_TIMESTAMP WHERE id=?",payment.get("id"),actor,id);c.commit();return payment;
  }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 public byte[] proof(long actor,boolean accountant,long id)throws SQLException{try(Connection c=KetNoiDB.getConnection()){
  var row=TruyVanDB.one(c,"SELECT proof_image FROM route_collections WHERE id=?"+(accountant?"":" AND collected_by=?"),accountant?new Object[]{id}:new Object[]{id,actor});if(row==null)throw new NoSuchElementException("Không tìm thấy biên lai trong phạm vi được phép");return (byte[])row.get("proof_image");
 }}
}
