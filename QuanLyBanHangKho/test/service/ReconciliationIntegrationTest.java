package service;
import db.TruyVanDB;import util.KetNoiDB;import java.sql.*;import java.util.*;import java.math.BigDecimal;import java.time.LocalDate;
public final class ReconciliationIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  long actor,other,customer;try(Connection c=KetNoiDB.getConnection()){actor=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.accountant'"));other=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.other'"));customer=TruyVanDB.id(TruyVanDB.one(c,"SELECT id FROM customers WHERE code='DL-S7'"));}
  var service=new DoiChieuCongNoService();var in=Map.<String,Object>of("customerId",customer,"from",LocalDate.now().withDayOfMonth(1).toString(),"to",LocalDate.now().toString());var r=service.create(actor,in);long id=((Number)r.get("id")).longValue();
  if(((BigDecimal)r.get("closing_balance")).compareTo(new BigDecimal("700.00"))!=0)throw new AssertionError("Cuối kỳ không khớp sổ");
  if(((Number)service.create(actor,in).get("id")).longValue()!=id)throw new AssertionError("Tạo trùng kỳ");
  try{service.detail(other,true,id);throw new AssertionError("Lộ biên bản đại lý khác");}catch(NoSuchElementException expected){}
  try{service.respond(other,id,Map.of("status","CONFIRMED"));throw new AssertionError("Xác nhận hộ đại lý khác");}catch(NoSuchElementException expected){}
  service.respond(actor,id,Map.of("status","DISPUTED","dealerBalance","650","reason","Chưa ghi nhận tiền mới chuyển"));
  try{service.respond(actor,id,Map.of("status","CONFIRMED"));throw new AssertionError("Ghi đè phản hồi");}catch(IllegalArgumentException expected){}
  new ThanhToanService().create(actor,Map.of("customerId",customer,"amount","50","paymentMethod","CASH","receivedDate",LocalDate.now().toString(),"requestKey",UUID.randomUUID().toString()));
  if(((BigDecimal)service.detail(actor,true,id).get("closing_balance")).compareTo(new BigDecimal("700.00"))!=0)throw new AssertionError("Snapshot bị đổi sau thu tiền");
  System.out.println("S7-06: số dư theo kỳ, chống trùng, snapshot và quyền phản hồi đều đạt");
 }
}
