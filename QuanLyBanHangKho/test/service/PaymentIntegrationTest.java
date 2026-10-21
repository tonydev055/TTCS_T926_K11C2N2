package service;
import db.TruyVanDB;
import util.KetNoiDB;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

/** Chạy sau SalesInvoiceIntegrationTest, chỉ trên database tạm riêng của lượt chạy. */
public final class PaymentIntegrationTest {
 static int checks;
 static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
 static void invalid(Callable<?> action,String label)throws Exception{try{action.call();throw new AssertionError(label);}catch(IllegalArgumentException expected){checks++;}}
 static BigDecimal balance(long id)throws Exception{try(Connection c=KetNoiDB.getConnection()){return (BigDecimal)TruyVanDB.one(c,"SELECT outstanding FROM receivable_invoice_balances WHERE id=?",id).get("outstanding");}}
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ chạy trên DB tạm riêng");
  long actor,customer,invoice;try(Connection c=KetNoiDB.getConnection()){
   actor=((Number)TruyVanDB.one(c,"SELECT id FROM users WHERE username='s7.accountant'").get("id")).longValue();
   customer=((Number)TruyVanDB.one(c,"SELECT id FROM customers WHERE code='DL-S7'").get("id")).longValue();
   invoice=((Number)TruyVanDB.one(c,"SELECT id FROM invoices ORDER BY id LIMIT 1").get("id")).longValue();
  }
  var service=new ThanhToanService();var input=new LinkedHashMap<String,Object>();
  input.put("customerId",customer);input.put("amount","200.00");input.put("paymentMethod","CASH");input.put("receivedDate",LocalDate.now().toString());input.put("note","Kiểm thử");input.put("requestKey",UUID.randomUUID().toString());
  var first=service.create(actor,input);long payment=((Number)first.get("id")).longValue();
  check(balance(invoice).compareTo(new BigDecimal("1100.00"))==0,"FIFO phải đối trừ hóa đơn cũ nhất");
  check(((Number)service.create(actor,input).get("id")).longValue()==payment,"Gửi lặp cùng requestKey không ghi trùng");
  invalid(()->{var v=new LinkedHashMap<>(input);v.put("amount","100000");v.put("requestKey",UUID.randomUUID().toString());return service.create(actor,v);},"Chặn thu vượt");
  invalid(()->{var v=new LinkedHashMap<>(input);v.put("invoiceId",Long.MAX_VALUE);v.put("requestKey",UUID.randomUUID().toString());return service.create(actor,v);},"Chặn hóa đơn không hợp lệ");
  invalid(()->{var v=new LinkedHashMap<>(input);v.put("amount","0.001");return service.create(actor,v);},"Chặn tiền quá 2 số lẻ");
  invalid(()->{var v=new LinkedHashMap<>(input);v.put("receivedDate",LocalDate.now().plusDays(1).toString());return service.create(actor,v);},"Chặn ngày tương lai");
  service.reverse(payment,actor,"Thu nhầm");check(balance(invoice).compareTo(new BigDecimal("1300.00"))==0,"Hoàn tác phải hoàn công nợ");
  invalid(()->service.reverse(payment,actor,"Lặp"),"Chặn hoàn tác lặp");
  try(Connection c=KetNoiDB.getConnection()){try{TruyVanDB.update(c,"DELETE FROM receivable_payments WHERE id=?",payment);throw new AssertionError("Xóa khoản thu");}catch(SQLException expected){check("P0001".equals(expected.getSQLState()),"DB chặn xóa khoản thu");}}
  var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);List<Future<Boolean>> results=new ArrayList<>();
  try{for(int i=0;i<2;i++)results.add(pool.submit(()->{start.await();var v=new LinkedHashMap<>(input);v.put("amount","1000");v.put("requestKey",UUID.randomUUID().toString());try{service.create(actor,v);return true;}catch(IllegalArgumentException expected){return false;}}));start.countDown();int success=0;for(var r:results)if(r.get(20,TimeUnit.SECONDS))success++;check(success==1,"Hai request đồng thời không được thu vượt tổng công nợ");}finally{pool.shutdownNow();}
  System.out.println("S7-02 integration: "+checks+" checks passed");
 }
}
