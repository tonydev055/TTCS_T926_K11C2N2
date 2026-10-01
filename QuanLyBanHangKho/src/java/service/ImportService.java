package service;
import dao.Sql;
import util.*;
import java.sql.*;
import java.util.*;

public class ImportService {
 public static List<String> headers(String kind,boolean cost){
  if(kind.equals("users"))return List.of("username","email","full_name","phone","role","warehouse_code","territory");
  if(!kind.equals("products"))throw new IllegalArgumentException("Loại nhập không hợp lệ");
  List<String> h=new ArrayList<>(List.of("sku","name","category_code","base_unit","packaging","active"));if(cost)h.add("cost_price");return h;
 }
 public List<Map<String,Object>> process(Connection c,String kind,List<XlsxService.Row> rows,boolean cost,boolean commit)throws SQLException{
  List<Map<String,Object>> results=new ArrayList<>();Set<String> seen=new HashSet<>(),emails=new HashSet<>();
  for(var row:rows){Savepoint point=c.setSavepoint();Map<String,Object> result=new LinkedHashMap<>();result.put("row",row.number());result.put("data",row.values());try{
    if(!row.error().isEmpty())throw new IllegalArgumentException(row.error());Map<String,Object> data=new LinkedHashMap<>(row.values());
    String key=CatalogService.text(data,kind.equals("users")?"username":"sku",true,80).toLowerCase(Locale.ROOT);if(!seen.add(key))throw new IllegalArgumentException("Mã bị trùng trong tệp");
    if(kind.equals("products")){
     String code=CatalogService.text(data,"category_code",true,40).toUpperCase(Locale.ROOT);var category=Sql.one(c,"SELECT id FROM categories WHERE code=?",code);if(category==null)throw new IllegalArgumentException("Không tìm thấy mã nhóm hàng "+code);data.put("category_id",category.get("id"));
     String enabled=CatalogService.text(data,"active",true,10).toLowerCase(Locale.ROOT);if(!Set.of("true","false","1","0").contains(enabled))throw new IllegalArgumentException("active phải là true/false hoặc 1/0");data.put("active",enabled.equals("true")||enabled.equals("1"));
     var existing=Sql.one(c,"SELECT id FROM products WHERE sku=?",key.toUpperCase(Locale.ROOT));new CatalogService().save(c,"products",existing==null?null:Sql.id(existing),data,cost);result.put("action",existing==null?"Tạo mới":"Cập nhật");
    }else{
     String username=CatalogService.text(data,"username",true,80),email=CatalogService.text(data,"email",true,255).toLowerCase(Locale.ROOT),name=CatalogService.text(data,"full_name",true,150),phone=CatalogService.phone(CatalogService.text(data,"phone",true,20),true);
     if(!username.matches("[A-Za-z0-9_.-]{3,80}"))throw new IllegalArgumentException("Tên đăng nhập chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang (3–80 ký tự)");
     if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Email không hợp lệ");if(!emails.add(email))throw new IllegalArgumentException("Email bị trùng trong tệp");
     if(Sql.one(c,"SELECT id FROM users WHERE lower(username)=lower(?) OR lower(email)=lower(?)",username,email)!=null)throw new IllegalArgumentException("Tài khoản hoặc email đã tồn tại");
     String role=CatalogService.text(data,"role",true,32);var roleRow=Sql.one(c,"SELECT id FROM roles WHERE code=?",role);if(roleRow==null)throw new IllegalArgumentException("Vai trò không tồn tại");
     String warehouse=CatalogService.text(data,"warehouse_code",false,32);var wh=warehouse.isEmpty()?null:Sql.one(c,"SELECT id FROM warehouses WHERE code=? AND active",warehouse);if(!warehouse.isEmpty()&&wh==null)throw new IllegalArgumentException("Mã kho không tồn tại hoặc đã ngừng hoạt động");if(Set.of("WAREHOUSE","WH_MANAGER").contains(role)&&wh==null)throw new IllegalArgumentException("Vai trò kho bắt buộc có mã kho");
     if(commit){String password=PasswordUtil.DEFAULT_USER_PASSWORD;long id=Sql.id(Sql.one(c,"INSERT INTO users(username,email,full_name,phone,password_hash,territory,requires_password_change) VALUES(?,?,?,?,?,?,true) RETURNING id",username,email,name,phone,PasswordUtil.encode(password),CatalogService.text(data,"territory",false,150)));Sql.update(c,"INSERT INTO user_roles(user_id,role_id) VALUES(?,?)",id,roleRow.get("id"));if(wh!=null)Sql.update(c,"INSERT INTO user_warehouses(user_id,warehouse_id) VALUES(?,?)",id,wh.get("id"));Sql.update(c,"INSERT INTO dev_mailbox_messages(recipient,subject,body) VALUES(?,?,?)",email,"Kích hoạt tài khoản KhoFlow","Tài khoản của bạn đã được tạo. Mật khẩu tạm thời: "+password);}
     result.put("action","Tạo mới");
    }
    result.put("valid",true);result.put("message",commit?"Đã nhập":"Hợp lệ");if(!commit)c.rollback(point);
   }catch(Exception e){c.rollback(point);result.put("valid",false);result.put("action","Bỏ qua");result.put("message",CatalogService.error(e));}finally{c.releaseSavepoint(point);}results.add(result);
  }return results;
 }
}
