package service;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public final class DebtStatementIntegrationTest {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}\\?currentSchema=s8_statement_test"))throw new IllegalStateException("Chỉ DB tạm");
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   s.execute("CREATE SCHEMA s8_statement_test");
   try{
    s.execute("CREATE TABLE customers(id bigint,code text,name text,region text); CREATE TABLE receivable_ledger_events(customer_id bigint,event_date date,recorded_at timestamp,reference text,amount numeric,kind text)");
    s.execute("INSERT INTO customers VALUES(1,'TEST','Đại lý kiểm thử','Bắc'),(2,'OTHER','Đại lý khác','Nam'); INSERT INTO receivable_ledger_events VALUES(1,'2026-01-01','2026-01-01','OPEN',1000,'INVOICE'),(1,'2026-01-02','2026-01-02 08:00','INV',500,'INVOICE'),(1,'2026-01-02','2026-01-02 09:00','PAY',-300,'PAYMENT'),(1,'2026-01-03','2026-01-03','ADJ',-100,'ADJUSTMENT'),(1,'2026-01-04','2026-01-04','REV',300,'REVERSAL'),(1,'2026-01-05','2026-01-05','LATER',999,'INVOICE'),(2,'2026-01-02','2026-01-02','OTHER',999,'INVOICE')");
    var service=new SoChiTietCongNoService();var data=service.report(1,"2026-01-02","2026-01-04");
    check(data,"openingBalance","1000");check(data,"debit","800");check(data,"credit","400");check(data,"closingBalance","1400");
    var rows=(List<Map<String,Object>>)data.get("items");if(rows.size()!=4)throw new AssertionError("Khoảng ngày phải gồm cả ngày cuối và không lẫn đại lý");
    String[] balances={"1500","1200","1100","1400"};for(int i=0;i<4;i++)check(rows.get(i),"balance",balances[i]);
    var empty=service.report(1,"2026-01-06","2026-01-06");check(empty,"openingBalance","2399");check(empty,"closingBalance","2399");if(!((List<?>)empty.get("items")).isEmpty())throw new AssertionError("Kỳ không phát sinh giữ số dư");
    try{service.report(999,"2026-01-01","2026-01-02");throw new AssertionError("Đại lý không tồn tại");}catch(NoSuchElementException expected){}
    try{service.report(1,"2026-01-03","2026-01-02");throw new AssertionError("Ngày đảo");}catch(IllegalArgumentException expected){}
    if(!"receivables.read".equals(security.PhanQuyen.permissionFor("/api/debt-statement/export","GET")))throw new AssertionError("Quyền export");
    System.out.println("S8-10: số dư đầu/cuối kỳ, tăng giảm, hoàn tác, thứ tự lũy kế, kỳ rỗng và đại lý không tồn tại đạt");
   }finally{s.execute("DROP SCHEMA s8_statement_test CASCADE");}
  }
 }
 private static void check(Map<String,Object> row,String key,String expected){if(((BigDecimal)row.get(key)).compareTo(new BigDecimal(expected))!=0)throw new AssertionError(key+": "+row.get(key));}
}
