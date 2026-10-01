package controller;
import dao.Sql;
import service.*;
import util.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet(urlPatterns={"/api/products/*","/api/categories/*","/api/product-units/*","/api/suppliers/*","/api/price-lists/*","/api/customer-groups/*"})
@MultipartConfig(maxFileSize=2097152,maxRequestSize=2200000)
public class CatalogServlet extends BaseServlet {
 private final CatalogService service=new CatalogService();
 @SuppressWarnings("unchecked") private List<String> roles(HttpServletRequest q){return (List<String>)q.getSession().getAttribute("roles");}
 private boolean cost(HttpServletRequest q){return roles(q).contains("SALES_MANAGER");}
 private boolean customer(HttpServletRequest q){return roles(q).stream().allMatch("CUSTOMER"::equals);}
 private long actor(HttpServletRequest q){return ((Number)q.getSession().getAttribute("userId")).longValue();}
 private String table(HttpServletRequest q){return CatalogService.table(q.getServletPath().substring("/api/".length()));}
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try(Connection c=DBConnection.getConnection()){
   String path=q.getPathInfo(),table=table(q);
   if(table.equals("products")&&path!=null&&path.matches("/\\d+/(image|thumbnail)")){
    long id=Long.parseLong(path.split("/")[1]);String column=path.endsWith("thumbnail")?"thumbnail":"image";
    var row=Sql.one(c,"SELECT "+column+" FROM products WHERE id=?"+(customer(q)?" AND active":""),id);if(row==null||row.get(column)==null){error(r,404,"Chưa có ảnh");return;}r.setContentType("image/png");r.setHeader("Cache-Control","private, no-store");r.getOutputStream().write((byte[])row.get(column));return;
   }
   var rows=service.list(c,table,cost(q),customer(q),actor(q));
   if(path!=null&&!path.equals("/")){long id=Long.parseLong(path.substring(1));rows=rows.stream().filter(row->Sql.id(row)==id).toList();if(rows.isEmpty()){error(r,404,"Không tìm thấy bản ghi");return;}}
   JsonUtil.send(r,200,Json.stringify(rows));
  }catch(Exception e){error(r,400,CatalogService.error(e));}
 }
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{mutate(q,r,false);}
 protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{mutate(q,r,false);}
 protected void doDelete(HttpServletRequest q,HttpServletResponse r)throws IOException{mutate(q,r,true);}
 private void mutate(HttpServletRequest q,HttpServletResponse r,boolean delete)throws IOException{
  try(Connection c=DBConnection.getConnection()){
   CatalogService.begin(c,actor(q));
   try{
    String table=table(q),path=q.getPathInfo();Long id=path==null||path.equals("/")?null:Long.valueOf(path.split("/")[1]);
    if(q.getMethod().equals("PUT")&&id==null)throw new IllegalArgumentException("Thiếu ID bản ghi");
    if(table.equals("products")&&path!=null&&path.matches("/\\d+/image")){
     if(!q.getMethod().equals("POST"))throw new IllegalArgumentException("Phương thức không hợp lệ");CatalogService.require(c,"products",id);Part file=q.getPart("file");if(file==null)throw new IllegalArgumentException("Chưa chọn ảnh");var images=ImageService.normalize(file.getInputStream().readAllBytes(),false);Sql.update(c,"UPDATE products SET image=?,thumbnail=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",images.image(),images.thumbnail(),id);
    }else if(delete){if(id==null)throw new IllegalArgumentException("Thiếu ID bản ghi");service.delete(c,table,id);}
    else {if(path!=null&&!path.equals("/")&&!path.matches("/\\d+"))throw new IllegalArgumentException("Đường dẫn không hợp lệ");String input=body(q);if(input.length()>2_000_000)throw new IllegalArgumentException("Dữ liệu quá lớn");id=service.save(c,table,id,Json.object(input),cost(q));}
    c.commit();JsonUtil.send(r,200,Json.stringify(Map.of("id",id,"message",delete?"Đã xóa dữ liệu":"Đã lưu dữ liệu")));
   }catch(Exception e){c.rollback();throw e;}
  }catch(Exception e){error(r,e instanceof SecurityException?403:400,CatalogService.error(e));}
 }
}
