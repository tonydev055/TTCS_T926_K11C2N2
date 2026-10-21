package service;
import java.sql.*;import java.util.*;import java.math.*;import java.nio.file.*;import util.KetNoiDB;import db.TruyVanDB;import service.*;
public class ReturnLotsIntegrationTest {
 static BigDecimal n(String x){return new BigDecimal(x);}static void check(boolean b,String label){if(!b)throw new AssertionError(label);System.out.println("PASS "+label);}
 static long insert(Connection c,String sql,Object... p)throws Exception{return TruyVanDB.id(TruyVanDB.one(c,sql,p));}
 static BigDecimal stock(long wh,long p)throws Exception{try(Connection c=KetNoiDB.getConnection()){return (BigDecimal)TruyVanDB.one(c,"SELECT on_hand FROM inventory WHERE warehouse_id=? AND product_id=?",wh,p).get("on_hand");}}
 public static void main(String[] args)throws Exception{
 if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Test DB only");
 long actor,reviewer,customer,wh,product,unit,order,orderLine,invoice,invoiceLine,bin,bin2,defect;
 try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
  for(String m:List.of("sprint8-returns.sql","sprint8-return-credits.sql"))s.execute(Files.readString(Path.of("database",m)));
  s.execute("INSERT INTO invoice_number_counters(document_type,last_number) VALUES('ADJUSTMENT',0) ON CONFLICT DO NOTHING");c.setAutoCommit(false);s.execute("SET LOCAL session_replication_role='replica'");
  actor=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('audit1','audit1@test.local','Actor test','x') RETURNING id");reviewer=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('audit2','audit2@test.local','Reviewer test','x') RETURNING id");
  long group=insert(c,"INSERT INTO customer_groups(code,name) VALUES('AUDIT','Audit') RETURNING id");customer=insert(c,"INSERT INTO customers(code,name,customer_group_id,region,sales_rep_id) VALUES('AUDIT','Audit',?,'Test',?) RETURNING id",group,actor);
  long category=insert(c,"INSERT INTO categories(code,name) VALUES('AUDIT','Audit') RETURNING id");product=insert(c,"INSERT INTO products(sku,name,category_id,base_unit) VALUES('AUDIT','Audit',?,'Cai') RETURNING id",category);unit=insert(c,"INSERT INTO product_units(product_id,name,factor) VALUES(?,'Cai',1) RETURNING id",product);
  wh=insert(c,"INSERT INTO warehouses(code,name) VALUES('AUDIT','Audit') RETURNING id");
  order=insert(c,"INSERT INTO orders(code,status,agent_id,created_by,warehouse_id,subtotal,discount_total,payable) VALUES('AUDIT','DELIVERED',?,?,?,1000,0,1000) RETURNING id",customer,actor,wh);
  orderLine=insert(c,"INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden,delivered_base_quantity) VALUES(?,?,?,10,1,100,0,1000,100,0,false,10) RETURNING id",order,product,unit);
  invoice=insert(c,"INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,due_date) VALUES('AUDIT',?,CURRENT_DATE,CURRENT_DATE,1000,0,1000,?,CURRENT_DATE) RETURNING id",customer,actor);
  invoiceLine=insert(c,"INSERT INTO invoice_lines(invoice_id,order_id,order_line_id,product_id,unit_id,delivered_quantity,unit_price,subtotal,discount_amount,line_total) VALUES(?,?,?,?,?,10,100,1000,0,1000) RETURNING id",invoice,order,orderLine,product,unit);
  TruyVanDB.update(c,"INSERT INTO inventory(warehouse_id,product_id,on_hand,reserved) VALUES(?,?,90,0)",wh,product);bin=insert(c,"INSERT INTO stock_bins(warehouse_id,product_id,on_hand) VALUES(?,?,90) RETURNING id",wh,product);
  TruyVanDB.update(c,"INSERT INTO stock_movements(warehouse_id,product_id,bin_id,delta,kind,document_id,line_id,actor_id) VALUES(?,?,?,-10,'SHIPMENT_OUT',?,?,?)",wh,product,bin,order,orderLine,actor);
    long loc=insert(c,"INSERT INTO warehouse_locations(warehouse_id,code,name) VALUES(?,'AUDIT2','Audit2') RETURNING id",wh);
  bin2=insert(c,"INSERT INTO stock_bins(warehouse_id,product_id,location_id,on_hand) VALUES(?,?,?,0) RETURNING id",wh,product,loc);
  TruyVanDB.update(c,"UPDATE stock_movements SET delta=-5 WHERE document_id=? AND kind='SHIPMENT_OUT'",order);
  TruyVanDB.update(c,"INSERT INTO stock_movements(warehouse_id,product_id,bin_id,delta,kind,document_id,line_id,actor_id) VALUES(?,?,?,-5,'SHIPMENT_OUT',?,?,?)",wh,product,bin2,order,orderLine,actor);
  defect=insert(c,"INSERT INTO warehouses(code,name,is_defect) VALUES('AUDIT-DEF','Audit defect',true) RETURNING id");
  s.execute("SET LOCAL session_replication_role='origin'");c.commit();
 }
 var returns=new TraHangService();long ret=returns.create(actor,List.of("SALES_MANAGER"),Map.of("invoiceId",invoice,"lines",List.of(Map.of("invoiceLineId",invoiceLine,"quantity","2","reason","QUALITY"))));
 check(stock(wh,product).compareTo(n("90"))==0,"S8-01 pending return does not increase inventory");
 try{returns.invoice(invoice,reviewer,List.of("SALES_REP"));throw new AssertionError("Out of scope");}catch(NoSuchElementException expected){System.out.println("PASS S8-01 assigned customer scope");}
 var detail=returns.detail(ret,actor,List.of("SALES_MANAGER"));long retLine=((Number)((Map<?,?>)((List<?>)detail.get("lines")).get(0)).get("id")).longValue();
 returns.receive(ret,reviewer,List.of("WH_MANAGER"),Map.of("lines",List.of(Map.of("lineId",retLine,"condition","GOOD"))));check(stock(wh,product).compareTo(n("92"))==0,"S8-01 confirmed return increases original lot");
  long defectReturn=returns.create(actor,List.of("SALES_MANAGER"),Map.of("invoiceId",invoice,"lines",List.of(Map.of("invoiceLineId",invoiceLine,"quantity","2","reason","DAMAGED"))));
 var defectDetail=returns.detail(defectReturn,actor,List.of("SALES_MANAGER"));long defectLine=((Number)((Map<?,?>)((List<?>)defectDetail.get("lines")).get(0)).get("id")).longValue();
 returns.receive(defectReturn,reviewer,List.of("WH_MANAGER"),Map.of("defectWarehouseId",defect,"lines",List.of(Map.of("lineId",defectLine,"condition","DEFECT","allocations",List.of(Map.of("binId",bin,"quantity","1"),Map.of("binId",bin2,"quantity","1"))))));
 check(stock(defect,product).compareTo(n("2"))==0,"Damaged return reaches defect warehouse");
 try(Connection c=KetNoiDB.getConnection()){
  check(((Number)TruyVanDB.one(c,"SELECT count(*) n FROM stock_movements WHERE kind='SALES_RETURN' AND document_id=?",defectReturn).get("n")).intValue()==1,"One movement per target bin");
  check(((Number)TruyVanDB.one(c,"SELECT count(*) n FROM sales_return_allocations WHERE return_line_id=?",defectLine).get("n")).intValue()==2,"Both source allocations retained");
 }
 try{returns.receive(defectReturn,reviewer,List.of("WH_MANAGER"),Map.of());throw new AssertionError("Double receive");}catch(IllegalArgumentException expected){System.out.println("PASS Double receive rejected");}
 }
}
