package service;
import util.*;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.io.*;
import java.util.zip.*;

public final class InventoryExportIntegrationTest {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}\\?currentSchema=s8_export_test"))throw new IllegalStateException("Chỉ chạy DB tạm");
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   s.execute("CREATE SCHEMA s8_export_test");
   try{
    s.execute("CREATE TABLE warehouses(id bigint,code text,name text,active boolean); CREATE TABLE categories(id bigint,parent_id bigint); CREATE TABLE products(id bigint,sku text,name text,base_unit text,category_id bigint,active boolean); CREATE TABLE inventory(warehouse_id bigint,product_id bigint,on_hand numeric,reserved numeric); CREATE TABLE user_warehouses(user_id bigint,warehouse_id bigint)");
    s.execute("INSERT INTO warehouses VALUES(1,'WH_TEST','Kiểm thử',true),(2,'OTHER','Không được cấp',true); INSERT INTO categories VALUES(1,null),(2,1); INSERT INTO products SELECT n,'SKU_'||n,'Sản phẩm','Cái',2,true FROM generate_series(1,25) n; INSERT INTO inventory SELECT 1,n,100,20 FROM generate_series(1,25) n; INSERT INTO user_warehouses VALUES(99,1)");
    var service=new TonKhoService();var f=Map.of("warehouseId","1","categoryId","1","state","RESERVED","q","SKU_","pageSize","20");
    var page=service.list(99,List.of(),f);var data=service.export(99,List.of(),f);
    if(((List<?>)page.get("items")).size()!=20||((List<?>)data.get("items")).size()!=25||!data.get("warehouseCode").equals("WH_TEST"))throw new AssertionError("Phải xuất toàn bộ bộ lọc qua nhiều trang, đúng mã kho");
    for(var r:(List<Map<String,Object>>)data.get("items"))for(String field:List.of("on_hand","reserved","available"))if(((BigDecimal)r.get(field)).compareTo(new BigDecimal(field.equals("on_hand")?"100":field.equals("reserved")?"20":"80"))!=0)throw new AssertionError("Ba cột tồn");
    try{service.export(99,List.of(),Map.of("warehouseId","2"));throw new AssertionError("Kho ngoài quyền");}catch(NoSuchElementException expected){}
    if(!((List<?>)service.export(99,List.of(),Map.of("q","notfound")).get("items")).isEmpty())throw new AssertionError("Bộ lọc rỗng");
    byte[] bytes=XuatExcelUtil.workbook(List.of("SKU","Tồn"),List.of(List.of("=1+1",new BigDecimal("80"))));
    boolean valid=false;try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().endsWith("sheet1.xml")){String xml=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);valid=xml.contains("inlineStr")&&!xml.contains("<f>")&&xml.contains("<v>80</v>");}}
    if(!valid)throw new AssertionError("XLSX phải giữ kiểu số và vô hiệu công thức từ chuỗi");
    System.out.println("S8-08: export đủ trang, bộ lọc nhóm con/từ khóa/trạng thái, ba cột tồn, kho ngoài quyền và XLSX đạt");
   }finally{s.execute("DROP SCHEMA s8_export_test CASCADE");}
  }
 }
}
