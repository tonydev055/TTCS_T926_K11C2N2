package service;
import db.TruyVanDB;
import util.KetNoiDB;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.lang.reflect.Proxy;
import java.io.*;
import jakarta.servlet.http.*;
import security.PhanQuyen;

public final class SalesReportIntegrationTest {
 static long insert(Connection c,String sql,Object... args)throws SQLException{return TruyVanDB.id(TruyVanDB.one(c,sql,args));}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  long actor,other,customer,second,product,unit;
  try(Connection c=KetNoiDB.getConnection();Statement s=c.createStatement()){
   c.setAutoCommit(false);s.execute("SET LOCAL session_replication_role='replica'");
   actor=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('sales-report1','sr1@test.local','Tên trùng','x') RETURNING id");other=insert(c,"INSERT INTO users(username,email,full_name,password_hash) VALUES('sales-report2','sr2@test.local','Tên trùng','x') RETURNING id");
   long group=insert(c,"INSERT INTO customer_groups(code,name) VALUES('REPORT','Kiểm thử') RETURNING id");
   customer=insert(c,"INSERT INTO customers(code,name,customer_group_id,region,sales_rep_id) VALUES('REPORT1','Kiểm thử 1',?,'Bắc',?) RETURNING id",group,actor);second=insert(c,"INSERT INTO customers(code,name,customer_group_id,region,sales_rep_id) VALUES('REPORT2','Kiểm thử 2',?,'Nam',?) RETURNING id",group,other);
   long category=insert(c,"INSERT INTO categories(code,name) VALUES('REPORT','Kiểm thử') RETURNING id");product=insert(c,"INSERT INTO products(sku,name,category_id,base_unit,cost_price) VALUES('REPORT','Kiểm thử',?,'Cái',2) RETURNING id",category);unit=insert(c,"INSERT INTO product_units(product_id,name,factor) VALUES(?,'Cái',1) RETURNING id",product);
   order(c,"REPORT1",customer,actor,product,unit,"2026-01-02","10","100");order(c,"REPORT2",customer,actor,product,unit,"2026-01-01","5","50");order(c,"REPORT3",second,other,product,unit,"2026-01-01","4","40");c.commit();
  }
  var service=new BaoCaoDoanhSoService();var f=new HashMap<String,String>(Map.of("from","2026-01-02","to","2026-01-02","groupBy","employee","compare","previous_period"));var roles=List.of("SALES_MANAGER");
  var data=service.report(roles,f);var rows=(List<Map<String,Object>>)data.get("items");if(rows.size()!=2)throw new AssertionError("Không gộp hai nhân viên trùng tên");
  check(rows.get(0),"revenue","100");check(rows.get(0),"gross_margin_pct","80");check(rows.get(1),"revenue_growth_pct","-100");
  for(String group:List.of("employee","region","dealer","category","sku")){f.put("groupBy",group);service.report(roles,f);}
  f.put("compare","none");if(((List<?>)service.report(roles,f).get("items")).size()!=1)throw new AssertionError("Không so sánh phải loại nhóm chỉ có kỳ trước");f.put("compare","previous_period");
  try{service.report(List.of("SALES_REP"),f);throw new AssertionError("Truy cập trái quyền");}catch(SecurityException expected){}
  var permissions=new ArrayList<>(PhanQuyen.permissions(List.of("ACCOUNTANT")));permissions.add("reports.sales");PhanQuyen.updateRole("ACCOUNTANT",permissions,actor);
  var restricted=service.report(List.of("ACCOUNTANT"),f);if(!Boolean.FALSE.equals(restricted.get("canViewMargin"))||((Map<?,?>)restricted.get("summary")).get("cogs")!=null||((Map<?,?>)((List<?>)restricted.get("items")).get(0)).get("gross_margin_pct")!=null)throw new AssertionError("Chỉ Quản lý kinh doanh xem được giá vốn/biên lợi nhuận");
  f.put("groupBy","sku;DROP");try{service.report(roles,f);throw new AssertionError("Chiều phân tích không hợp lệ");}catch(IllegalArgumentException expected){}
  filter(actor,null,401);filter(actor,List.of("WAREHOUSE"),403);filter(actor,roles,200);
  System.out.println("S8-06: năm chiều, so sánh giảm 100%, tên trùng, bảo mật biên lợi nhuận và API 401/403 đạt");
 }
 static void order(Connection c,String code,long customer,long actor,long product,long unit,String date,String qty,String amount)throws SQLException{
  long order=insert(c,"INSERT INTO orders(code,status,agent_id,created_by,created_at,subtotal,discount_total,payable) VALUES(?,'APPROVED',?,?,?::timestamp,?,0,?) RETURNING id",code,customer,actor,date,new BigDecimal(amount),new BigDecimal(amount));
  TruyVanDB.update(c,"INSERT INTO order_lines(order_id,product_id,unit_id,quantity,conversion_factor,base_price,discount_amount,line_total,list_price,floor_price,price_overridden) VALUES(?,?,?,?,1,10,0,?,10,0,false)",order,product,unit,new BigDecimal(qty),new BigDecimal(amount));
 }
 static void check(Map<String,Object> r,String field,String expected){if(((BigDecimal)r.get(field)).compareTo(new BigDecimal(expected))!=0)throw new AssertionError(field+": "+r.get(field));}
 static void filter(long actor,List<String> roles,int expected)throws Exception{
  int version=new db.NguoiDungDB().sessionVersion(actor);Map<String,Object> attributes=roles==null?Map.of():Map.of("userId",actor,"roles",roles,"sessionVersion",version);
  HttpSession session=(HttpSession)Proxy.newProxyInstance(HttpSession.class.getClassLoader(),new Class[]{HttpSession.class},(p,m,a)->m.getName().equals("getAttribute")?attributes.get(a[0]):null);
  HttpServletRequest request=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){case "getServletPath"->"/api/sales-report";case "getPathInfo"->"/export";case "getMethod"->"GET";case "getSession"->roles==null?null:session;default->null;});
  int[] status={200};boolean[] called={false};var writer=new PrintWriter(new StringWriter());
  HttpServletResponse response=(HttpServletResponse)Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{if(m.getName().equals("setStatus"))status[0]=(Integer)a[0];if(m.getName().equals("getWriter"))return writer;return null;});
  new filter.XacThucFilter().doFilter(request,response,(q,r)->called[0]=true);if(status[0]!=expected||called[0]!=(expected==200))throw new AssertionError("API filter status "+status[0]);
 }
}
