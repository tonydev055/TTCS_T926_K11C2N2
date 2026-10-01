package service;

import dao.Sql;
import util.*;
import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class CatalogService {
 public static final Set<String> TABLES=Set.of("products","categories","product_units","suppliers","customer_groups","price_lists");
 public static String text(Map<String,Object> data,String key,boolean required,int max){String x=Objects.toString(data.get(key),"").trim();if((required&&x.isEmpty())||x.length()>max)throw new IllegalArgumentException("Trường "+key+" không hợp lệ (tối đa "+max+" ký tự)");return x;}
 public static long integer(Map<String,Object> data,String key){try{return new BigDecimal(Objects.toString(data.get(key),"0")).longValueExact();}catch(Exception e){throw new IllegalArgumentException("Trường "+key+" phải là số nguyên");}}
 public static BigDecimal decimal(Map<String,Object> data,String key,boolean positive,int scale){try{BigDecimal v=new BigDecimal(Objects.toString(data.get(key),""));if(v.signum()<(positive?1:0)||v.scale()>scale||v.precision()-v.scale()>12)throw new Exception();return v;}catch(Exception e){throw new IllegalArgumentException("Trường "+key+" phải là số "+(positive?"dương":"không âm")+", tối đa "+scale+" chữ số thập phân");}}
 public static boolean active(Map<String,Object> data){Object v=data.getOrDefault("active",true);if(!(v instanceof Boolean))throw new IllegalArgumentException("Trạng thái không hợp lệ");return (Boolean)v;}
 public static String phone(String value,boolean required){String p=value.trim().replaceAll("[ .-]","");if(p.isEmpty()&&!required)return p;if(!p.matches("(?:0|\\+84)[35789][0-9]{8}"))throw new IllegalArgumentException("Số điện thoại Việt Nam không hợp lệ");return p;}
 public static void begin(Connection c,long actor)throws SQLException{c.setAutoCommit(false);Sql.rows(c,"SELECT pg_advisory_xact_lock(260902)");Sql.rows(c,"SELECT set_config('app.actor_id',?,true)",String.valueOf(actor));}
 public static String table(String endpoint){String t=endpoint.replace('-','_');if(!TABLES.contains(t))throw new IllegalArgumentException("Chức năng không tồn tại");return t;}

 public List<Map<String,Object>> list(Connection c,String table,boolean cost,boolean customer,long actor)throws SQLException{
  if(table.equals("products"))return Sql.rows(c,"SELECT p.id,p.sku,p.name,p.category_id,c.name category_name,p.base_unit,p.packaging,p.active,p.updated_at,(p.image IS NOT NULL) has_image"+(cost?",p.cost_price":"")+" FROM products p JOIN categories c ON c.id=p.category_id "+(customer?"WHERE p.active=true ":"")+"ORDER BY p.id DESC");
  if(table.equals("product_units"))return Sql.rows(c,"SELECT u.*,p.sku,p.name product_name FROM product_units u JOIN products p ON p.id=u.product_id WHERE u.active=true ORDER BY p.sku,u.factor");
  if(table.equals("price_lists")){
   var lists=Sql.rows(c,"SELECT p.*,g.name group_name,EXISTS(SELECT 1 FROM orders o WHERE o.price_list_id=p.id) locked FROM price_lists p JOIN customer_groups g ON g.id=p.customer_group_id "+(customer?"WHERE p.active AND CURRENT_DATE BETWEEN p.valid_from AND p.valid_to AND p.customer_group_id=(SELECT customer_group_id FROM users WHERE id=?) ":"")+"ORDER BY p.id DESC",customer?new Object[]{actor}:new Object[]{});
   for(var p:lists)p.put("lines",Sql.rows(c,"SELECT l.product_id,p.sku,p.name product_name,p.base_unit,l.price"+(customer?"":",l.floor_price")+" FROM price_list_lines l JOIN products p ON p.id=l.product_id WHERE l.price_list_id=? ORDER BY p.sku",p.get("id")));
   return lists;
  }
  if(customer&&table.equals("customer_groups"))return Sql.rows(c,"SELECT g.* FROM customer_groups g JOIN users u ON u.customer_group_id=g.id WHERE u.id=?",actor);
  return Sql.rows(c,"SELECT * FROM "+table+" ORDER BY id DESC");
 }

 public long save(Connection c,String table,Long id,Map<String,Object> data,boolean cost)throws SQLException{
  Map<String,Object> old=id==null?null:Sql.one(c,"SELECT * FROM "+table+" WHERE id=? FOR UPDATE",id);
  if(id!=null&&old==null)throw new IllegalArgumentException("Không tìm thấy bản ghi");
  Map<String,Object> values=new LinkedHashMap<>();
  switch(table){
   case "products" -> {
    values.put("sku",text(data,"sku",true,80).toUpperCase(Locale.ROOT));values.put("name",text(data,"name",true,200));
    long category=integer(data,"category_id");require(c,"categories",category);values.put("category_id",category);
    String base=text(data,"base_unit",true,40);if(old!=null&&!base.equals(old.get("base_unit")))throw new IllegalArgumentException("Không đổi đơn vị cơ sở sau khi tạo SKU; hãy thêm đơn vị quy đổi");
    values.put("base_unit",base);values.put("packaging",text(data,"packaging",false,255));values.put("active",active(data));
    if(data.containsKey("cost_price")&&!cost)throw new SecurityException("Chỉ Quản lý kinh doanh được sửa giá vốn");
    if(cost)values.put("cost_price",decimal(data,"cost_price",false,2));
    values.put("updated_at",Timestamp.valueOf(java.time.LocalDateTime.now()));
   }
   case "categories" -> {
    values.put("code",text(data,"code",true,40).toUpperCase(Locale.ROOT));values.put("name",text(data,"name",true,150));
    Long parent=data.get("parent_id")==null||Objects.toString(data.get("parent_id"),"").isBlank()?null:integer(data,"parent_id");
    if(parent!=null){require(c,"categories",parent);Set<Long> visited=new HashSet<>();Long cursor=parent;while(cursor!=null){if(Objects.equals(cursor,id)||!visited.add(cursor))throw new IllegalArgumentException("Nhóm cha tạo vòng lặp");Object next=Sql.one(c,"SELECT parent_id FROM categories WHERE id=?",cursor).get("parent_id");cursor=next==null?null:((Number)next).longValue();}}
    values.put("parent_id",parent);
   }
   case "suppliers" -> {
    values.put("code",text(data,"code",true,40).toUpperCase(Locale.ROOT));values.put("name",text(data,"name",true,200));values.put("tax_code",text(data,"tax_code",true,40));values.put("contact_name",text(data,"contact_name",true,150));values.put("phone",phone(text(data,"phone",false,20),false));values.put("payment_terms",text(data,"payment_terms",true,2000));values.put("active",active(data));
   }
   case "customer_groups" -> {values.put("code",text(data,"code",true,40).toUpperCase(Locale.ROOT));values.put("name",text(data,"name",true,150));}
   case "product_units" -> {
    long product=integer(data,"product_id");var p=require(c,"products",product);String name=text(data,"name",true,40);BigDecimal factor=decimal(data,"factor",true,6);
    if(old!=null&&(!Boolean.TRUE.equals(old.get("active"))||((Number)old.get("product_id")).longValue()!=product))throw new IllegalArgumentException("Đơn vị đã thay đổi; hãy tải lại");
    if(name.equalsIgnoreCase(String.valueOf(p.get("base_unit")))&&factor.compareTo(BigDecimal.ONE)!=0)throw new IllegalArgumentException("Đơn vị cơ sở phải có hệ số bằng 1");
    if(old!=null&&String.valueOf(old.get("name")).equalsIgnoreCase(String.valueOf(p.get("base_unit"))))throw new IllegalArgumentException("Không sửa đơn vị cơ sở");
    if(old!=null){Sql.update(c,"UPDATE product_units SET active=false WHERE id=?",id);id=null;}
    values.put("product_id",product);values.put("name",name);values.put("factor",factor);
   }
   case "price_lists" -> {return savePrices(c,id,data);}
   default -> throw new IllegalArgumentException("Chức năng không tồn tại");
  }
  long saved=write(c,table,id,values);
  if(table.equals("products")&&id==null)Sql.update(c,"INSERT INTO product_units(product_id,name,factor) VALUES(?,?,1)",saved,values.get("base_unit"));
  return saved;
 }
 public static Map<String,Object> require(Connection c,String table,long id)throws SQLException{var row=Sql.one(c,"SELECT * FROM "+table+" WHERE id=?",id);if(row==null)throw new IllegalArgumentException("Không tìm thấy "+table+" #"+id);return row;}
 public static long write(Connection c,String table,Long id,Map<String,Object> values)throws SQLException{
  List<Object> args=new ArrayList<>(values.values());
  if(id==null){String sql="INSERT INTO "+table+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+") RETURNING id";return Sql.id(Sql.one(c,sql,args.toArray()));}
  args.add(id);Sql.update(c,"UPDATE "+table+" SET "+String.join(",",values.keySet().stream().map(k->k+"=?").toList())+" WHERE id=?",args.toArray());return id;
 }
 @SuppressWarnings("unchecked") private long savePrices(Connection c,Long id,Map<String,Object> data)throws SQLException{
  if(id!=null&&Sql.one(c,"SELECT id FROM orders WHERE price_list_id=? LIMIT 1",id)!=null)throw new IllegalArgumentException("Bảng giá đã phát sinh đơn; hãy tạo phiên bản mới");
  long group=integer(data,"customer_group_id");require(c,"customer_groups",group);
  LocalDate from,to;try{from=LocalDate.parse(text(data,"valid_from",true,10));to=LocalDate.parse(text(data,"valid_to",true,10));}catch(Exception e){throw new IllegalArgumentException("Ngày hiệu lực không hợp lệ");}if(to.isBefore(from))throw new IllegalArgumentException("Ngày kết thúc phải từ ngày bắt đầu trở đi");
  Long previous=data.get("previous_id")==null?null:integer(data,"previous_id");int version=1;
  if(id!=null){var old=require(c,"price_lists",id);previous=old.get("previous_id")==null?null:((Number)old.get("previous_id")).longValue();version=((Number)old.get("version")).intValue();}
  else if(previous!=null){var old=require(c,"price_lists",previous);if(((Number)old.get("customer_group_id")).longValue()!=group)throw new IllegalArgumentException("Phiên bản mới phải cùng nhóm khách hàng");version=((Number)Sql.one(c,"WITH RECURSIVE chain AS (SELECT id,version FROM price_lists WHERE id=? UNION ALL SELECT p.id,p.version FROM price_lists p JOIN chain c ON p.previous_id=c.id) SELECT max(version) v FROM chain",previous).get("v")).intValue()+1;}
  boolean enabled=active(data);
  if(enabled&&Sql.one(c,"SELECT id FROM price_lists WHERE customer_group_id=? AND active AND id<>? AND valid_from<=? AND valid_to>=? LIMIT 1",group,id==null?0:id,java.sql.Date.valueOf(to),java.sql.Date.valueOf(from))!=null)throw new IllegalArgumentException("Thời gian trùng bảng giá đang áp dụng của nhóm này. Hãy điều chỉnh ngày hoặc ngừng áp dụng bảng cũ");
  Object raw=data.get("lines");if(!(raw instanceof List<?> lines)||lines.isEmpty()||lines.size()>5000)throw new IllegalArgumentException("Bảng giá phải có từ 1 đến 5000 dòng");
  Set<Long> products=new HashSet<>();List<Map<String,Object>> entries=new ArrayList<>();for(Object row:lines){if(!(row instanceof Map))throw new IllegalArgumentException("Dòng giá không hợp lệ");Map<String,Object> line=(Map<String,Object>)row;long product=integer(line,"product_id");require(c,"products",product);if(!products.add(product))throw new IllegalArgumentException("Sản phẩm bị lặp trong bảng giá");BigDecimal price=decimal(line,"price",false,2),floor=decimal(line,"floor_price",false,2);if(floor.compareTo(price)>0)throw new IllegalArgumentException("Giá sàn không được lớn hơn giá bán");entries.add(Map.of("product_id",product,"price",price,"floor_price",floor));}
  Map<String,Object> values=new LinkedHashMap<>();values.put("name",text(data,"name",true,150));values.put("customer_group_id",group);values.put("valid_from",java.sql.Date.valueOf(from));values.put("valid_to",java.sql.Date.valueOf(to));values.put("active",enabled);values.put("previous_id",previous);values.put("version",version);
  long saved=write(c,"price_lists",id,values);if(id!=null)Sql.update(c,"DELETE FROM price_list_lines WHERE price_list_id=?",id);
  for(var line:entries)Sql.update(c,"INSERT INTO price_list_lines(price_list_id,product_id,price,floor_price) VALUES(?,?,?,?)",saved,line.get("product_id"),line.get("price"),line.get("floor_price"));return saved;
 }
 public void delete(Connection c,String table,long id)throws SQLException{
  var old=require(c,table,id);
  if(table.equals("categories")&&(Sql.one(c,"SELECT id FROM products WHERE category_id=? LIMIT 1",id)!=null||Sql.one(c,"SELECT id FROM categories WHERE parent_id=? LIMIT 1",id)!=null))throw new IllegalArgumentException("Nhóm còn sản phẩm hoặc nhóm con, không thể xóa");
  if(table.equals("product_units")){var p=require(c,"products",((Number)old.get("product_id")).longValue());if(old.get("name").toString().equalsIgnoreCase(p.get("base_unit").toString()))throw new IllegalArgumentException("Không xóa đơn vị cơ sở");Sql.update(c,"UPDATE product_units SET active=false WHERE id=?",id);return;}
  Sql.update(c,"DELETE FROM "+table+" WHERE id=?",id);
 }
 public static String error(Exception e){if(e instanceof SQLException sql){return switch(Objects.toString(sql.getSQLState(),"")){case "23505"->"Mã đã tồn tại. Vui lòng dùng mã khác";case "23503"->"Dữ liệu đã được sử dụng, không thể xóa; hãy chuyển sang ngừng hoạt động";case "23514","23502","22003"->"Dữ liệu không đáp ứng điều kiện hợp lệ";default->"Không thể xử lý dữ liệu. Vui lòng thử lại";};}return e.getMessage()==null?"Không thể xử lý yêu cầu":e.getMessage();}
}
