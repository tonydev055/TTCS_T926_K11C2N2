package service;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

public final class OverdueReportIntegrationTest {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}\\?currentSchema=s8_overdue_test"))throw new IllegalStateException("Chỉ chạy DB tạm");
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   s.execute("CREATE SCHEMA s8_overdue_test");
   try{
    s.execute("CREATE TABLE users(id bigint,full_name text); CREATE TABLE customers(id bigint,code text,name text,region text,sales_rep_id bigint); CREATE TABLE receivable_invoice_balances(customer_id bigint,due_date date,outstanding numeric)");
    s.execute("INSERT INTO users VALUES(10,'Người phụ trách'); INSERT INTO customers VALUES(1,'A','Đại lý A','Bắc',10),(2,'B','Đại lý B','Nam',null); INSERT INTO receivable_invoice_balances SELECT 1,CURRENT_DATE-n,100 FROM unnest(ARRAY[1,30,31,60,61,90,91]) n; INSERT INTO receivable_invoice_balances VALUES(1,CURRENT_DATE,999),(1,CURRENT_DATE+1,999),(1,CURRENT_DATE-99,0),(2,CURRENT_DATE-100,50)");
    var service=new BaoCaoQuaHanService();var data=service.report(Map.of());var rows=(List<Map<String,Object>>)data.get("items");if(rows.size()!=2||!rows.get(0).get("code").equals("A"))throw new AssertionError("Xếp hạng theo giá trị trên 90 ngày trước");
    var row=rows.get(0);check(row,"d1","200");check(row,"d31","200");check(row,"d61","200");check(row,"d91","100");check(row,"overdue","700");check((Map<String,Object>)data.get("summary"),"overdue","750");
    var filtered=service.report(Map.of("staffId","unassigned","bucket","d91"));if(((List<?>)filtered.get("items")).size()!=1)throw new AssertionError("Lọc nhân viên chưa phân công");
    if(((List<?>)service.report(Map.of("staffId","10","region","Bắc","bucket","d31")).get("items")).size()!=1)throw new AssertionError("Lọc kết hợp");
    if(!((List<?>)service.report(Map.of("q","không tồn tại")).get("items")).isEmpty())throw new AssertionError("Rỗng");
    try{service.report(Map.of("bucket","BAD"));throw new AssertionError("Nhóm không hợp lệ");}catch(IllegalArgumentException expected){}
    if(!"receivables.read".equals(security.PhanQuyen.permissionFor("/api/overdue-report/export","GET")))throw new AssertionError("Quyền export");
    System.out.println("S8-09: biên tuổi quá hạn 1/30/31/60/61/90/91, loại nợ chưa đến hạn, xếp hạng và bộ lọc đạt");
   }finally{s.execute("DROP SCHEMA s8_overdue_test CASCADE");}
  }
 }
 private static void check(Map<String,Object> row,String key,String expected){if(((BigDecimal)row.get(key)).compareTo(new BigDecimal(expected))!=0)throw new AssertionError(key+": "+row.get(key));}
}
