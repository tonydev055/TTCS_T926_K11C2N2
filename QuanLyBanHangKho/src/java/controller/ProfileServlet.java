package controller;
import dao.*;
import service.*;
import util.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/api/profile/*")
@MultipartConfig(maxFileSize=2097152,maxRequestSize=2200000)
public class ProfileServlet extends BaseServlet {
 private long actor(HttpServletRequest q){return ((Number)q.getSession().getAttribute("userId")).longValue();}
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try(Connection c=DBConnection.getConnection()){
   String path=q.getPathInfo();if("/avatar".equals(path)||"/thumbnail".equals(path)){String column=path.equals("/avatar")?"avatar":"avatar_thumbnail";var row=Sql.one(c,"SELECT "+column+" FROM users WHERE id=?",actor(q));if(row==null||row.get(column)==null){error(r,404,"Chưa có ảnh đại diện");return;}r.setContentType("image/png");r.setHeader("Cache-Control","private, no-store");r.getOutputStream().write((byte[])row.get(column));return;}
   var row=Sql.one(c,"SELECT id,username,email,full_name,phone,territory,(avatar IS NOT NULL) has_avatar FROM users WHERE id=?",actor(q));row.put("roles",new UserDAO().roles(actor(q)));row.put("warehouses",new UserDAO().warehouses(actor(q)));JsonUtil.send(r,200,Json.stringify(row));
  }catch(Exception e){error(r,400,CatalogService.error(e));}
 }
 protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try(Connection c=DBConnection.getConnection()){
   var data=Json.object(body(q));if(!Set.of("full_name","phone").containsAll(data.keySet()))throw new IllegalArgumentException("Chỉ được sửa họ tên và số điện thoại");String name=CatalogService.text(data,"full_name",true,150),phone=CatalogService.phone(CatalogService.text(data,"phone",true,20),true);
   Sql.update(c,"UPDATE users SET full_name=?,phone=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",name,phone,actor(q));q.getSession().setAttribute("fullName",name);ok(r,"{\"message\":\"Đã cập nhật hồ sơ\"}");
  }catch(Exception e){error(r,400,CatalogService.error(e));}
 }
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try(Connection c=DBConnection.getConnection()){
   if(!"/avatar".equals(q.getPathInfo()))throw new IllegalArgumentException("Đường dẫn không hợp lệ");Part file=q.getPart("file");if(file==null)throw new IllegalArgumentException("Chưa chọn ảnh");var images=ImageService.normalize(file.getInputStream().readAllBytes(),true);Sql.update(c,"UPDATE users SET avatar=?,avatar_thumbnail=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",images.image(),images.thumbnail(),actor(q));ok(r,"{\"message\":\"Đã cập nhật ảnh đại diện\"}");
  }catch(Exception e){error(r,400,CatalogService.error(e));}
 }
}
