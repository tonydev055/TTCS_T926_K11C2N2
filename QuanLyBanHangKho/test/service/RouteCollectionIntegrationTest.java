package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.util.*;import java.time.LocalDate;import java.awt.image.BufferedImage;import java.io.ByteArrayOutputStream;import javax.imageio.ImageIO;
public final class RouteCollectionIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  long accountant,sales,customer;try(Connection c=KetNoiDB.getConnection()){
   accountant=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.accountant'"));customer=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM customers WHERE code='DL-S7'"));
   sales=TruyVanDB.id(TruyVanDB.one(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('s7.sales','sales@s7.test','Nhân viên tuyến','x') RETURNING id"));TruyVanDB.update(c,"INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='SALES_REP'",sales);TruyVanDB.update(c,"UPDATE customers SET sales_rep_id=? WHERE id=?",sales,customer);
  }
  var image=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB),"png",image);byte[] photo=image.toByteArray();var service=new ThuTienTheoTuyenService();var input=new LinkedHashMap<String,Object>(Map.of("customerId",customer,"amount","50","collectedDate",LocalDate.now().toString(),"requestKey",UUID.randomUUID().toString()));
  long id=service.create(sales,input,photo);if(service.create(sales,input,photo)!=id)throw new AssertionError("Ghi trùng khoản thu");
  try{service.proof(accountant,false,id);throw new AssertionError("Lộ biên lai");}catch(NoSuchElementException expected){}
  var first=service.accept(accountant,id);if(!first.get("id").equals(service.accept(accountant,id).get("id")))throw new AssertionError("Nhận trùng tiền");
  input.put("requestKey",UUID.randomUUID().toString());long second=service.create(sales,input,photo);
  long before;try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   before=((Number)TruyVanDB.one(c,"SELECT count(*) n FROM receivable_payments").get("n")).longValue();s.execute("CREATE FUNCTION s7_test_collection_fail() RETURNS trigger LANGUAGE plpgsql AS 'BEGIN RAISE EXCEPTION ''test rollback''; END'");s.execute("CREATE TRIGGER s7_test_collection_fail AFTER UPDATE ON route_collections FOR EACH ROW EXECUTE FUNCTION s7_test_collection_fail()");
  }
  try{service.accept(accountant,second);throw new AssertionError("Không kích hoạt lỗi");}catch(SQLException expected){}
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   if(((Number)TruyVanDB.one(c,"SELECT count(*) n FROM receivable_payments").get("n")).longValue()!=before)throw new AssertionError("Khoản thu không rollback cùng xác nhận");
   if(!"PENDING".equals(TruyVanDB.one(c,"SELECT status FROM route_collections WHERE id=?",second).get("status")))throw new AssertionError("Mất trạng thái chờ nộp");s.execute("DROP TRIGGER s7_test_collection_fail ON route_collections");s.execute("DROP FUNCTION s7_test_collection_fail()");
  }
  service.accept(accountant,second);System.out.println("S7-05: ảnh biên lai, chống trùng, phạm vi và rollback nguyên tử đạt");
 }
}
