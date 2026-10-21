package service;
import util.KetNoiDB;
import db.TruyVanDB;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

/** Chỉ chạy trên database tạm; schema riêng tự dọn sau kiểm thử. */
public final class InventoryReportIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}\\?currentSchema=s8_inventory_test"))throw new IllegalStateException("Chỉ chạy DB tạm, schema s8_inventory_test");
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   s.execute("CREATE SCHEMA s8_inventory_test");
   try{
    s.execute("CREATE TABLE warehouses(id bigint,code text,name text,active boolean); CREATE TABLE categories(id bigint,name text,parent_id bigint); CREATE TABLE products(id bigint,sku text,name text,base_unit text,category_id bigint,cost_price numeric); CREATE TABLE inventory(warehouse_id bigint,product_id bigint,on_hand numeric); CREATE TABLE user_warehouses(user_id bigint,warehouse_id bigint); CREATE TABLE stock_movements(warehouse_id bigint,product_id bigint,delta numeric,kind text,created_at timestamp)");
    s.execute("INSERT INTO warehouses VALUES(1,'TEST','Kho kiểm thử',true),(2,'OTHER','Ngoài phạm vi',true); INSERT INTO categories VALUES(1,'Nhóm kiểm thử',null); INSERT INTO products VALUES(1,'TEST','Kiểm thử','Cái',1,10),(2,'STATIC','Không xuất','Cái',1,20); INSERT INTO inventory VALUES(1,1,80),(1,2,10),(2,1,999); INSERT INTO user_warehouses VALUES(99,1); INSERT INTO stock_movements VALUES(1,1,-20,'SHIPMENT_OUT','2026-01-02 00:00:00')");
    s.execute("CREATE TABLE stock_bins(id bigint,warehouse_id bigint,product_id bigint); CREATE TABLE sales_returns(id bigint,status text,decided_at timestamp); CREATE TABLE sales_return_lines(id bigint,return_id bigint); CREATE TABLE sales_return_allocations(source_bin_id bigint,return_line_id bigint,base_quantity numeric)");
    var data=new BaoCaoTonKhoService().report(99,List.of(),Map.of("from","2026-01-01","to","2026-01-02","slowDays","5"));
    var rows=(List<Map<String,Object>>)data.get("items");if(rows.size()!=2)throw new AssertionError("Phải giới hạn kho được cấp");
    var r=rows.stream().filter(x->x.get("sku").equals("TEST")).findFirst().orElseThrow();
    check(r,"opening","100");check(r,"closing","80");check(r,"average_stock","90");check(r,"inventory_days","9");check(r,"stock_value","800");
    var idle=rows.stream().filter(x->x.get("sku").equals("STATIC")).findFirst().orElseThrow();if(idle.get("inventory_days")!=null||!Boolean.TRUE.equals(idle.get("slow")))throw new AssertionError("Hàng không xuất phải chỉ rõ chậm luân chuyển");
    try{new BaoCaoTonKhoService().report(99,List.of(),Map.of("warehouseId","2"));throw new AssertionError("Kho ngoài quyền");}catch(NoSuchElementException expected){}
    if(!((List<?>)new BaoCaoTonKhoService().report(99,List.of(),Map.of("q","không tồn tại")).get("items")).isEmpty())throw new AssertionError("Lọc rỗng");
    s.execute("INSERT INTO stock_bins VALUES(10,1,1); INSERT INTO sales_returns VALUES(1,'RECEIVED','2026-01-02 12:00:00'),(2,'PENDING','2026-01-02 12:00:00'),(3,'RECEIVED','2026-01-03 12:00:00'); INSERT INTO sales_return_lines VALUES(1,1),(2,2),(3,3); INSERT INTO sales_return_allocations VALUES(10,1,20),(10,2,99),(10,3,99)");
    var returned=(List<Map<String,Object>>)new BaoCaoTonKhoService().report(99,List.of(),Map.of("from","2026-01-01","to","2026-01-02")).get("items");
    var returnedSku=returned.stream().filter(x->x.get("sku").equals("TEST")).findFirst().orElseThrow();check(returnedSku,"shipped","0");check(returnedSku,"turnover","0");
    if(returnedSku.get("inventory_days")!=null)throw new AssertionError("Trả hết hàng: không được báo vòng quay dương; loại phiếu chờ nhận/ngoài kỳ");
    System.out.println("S8-07: tồn đầu/cuối kỳ, bình quân thời gian, ngày tồn, hàng không xuất và phạm vi kho đạt");
   }finally{s.execute("DROP SCHEMA s8_inventory_test CASCADE");}
  }
 }
 private static void check(Map<String,Object> row,String key,String expected){if(((BigDecimal)row.get(key)).compareTo(new BigDecimal(expected))!=0)throw new AssertionError(key+": "+row.get(key));}
}
