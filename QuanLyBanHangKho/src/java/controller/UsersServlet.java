package controller;
import dao.UserDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import service.EmailService;
import util.*;
import java.io.*;
import java.util.*;
@WebServlet("/api/users/*")
public class UsersServlet extends BaseServlet {
 private final UserDAO dao=new UserDAO();
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
   try{String p=q.getPathInfo();if(p!=null&&!p.equals("/")){var row=dao.findById(Long.parseLong(p.substring(1)));if(row==null){error(r,404,"Không tìm thấy tài khoản");return;}ok(r,jsonRows(List.of(row)));return;}
     int page=Math.max(1,intParam(q,"page",1)),size=Math.min(100,Math.max(1,intParam(q,"size",20)));String search=q.getParameter("search"),role=q.getParameter("role"),status=q.getParameter("status");var rows=dao.search(search,role,status,page,size);String stats=jsonRows(List.of(dao.stats()));stats=stats.substring(1,stats.length()-1);ok(r,"{\"items\":"+jsonRows(rows)+",\"page\":"+page+",\"size\":"+size+",\"total\":"+dao.count(search,role,status)+",\"stats\":"+stats+"}");
   }catch(Exception e){error(r,400,e.getMessage());}
 }
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{
   try{String b=body(q),path=q.getPathInfo();if(path!=null&&path.matches("/\\d+/(lock|unlock)")){String[] x=path.split("/");long id=Long.parseLong(x[1]);if(id==((Number)q.getSession().getAttribute("userId")).longValue()&&"lock".equals(x[2]))throw new IllegalArgumentException("Không thể tự khoá tài khoản của chính mình");dao.setLocked(id,"lock".equals(x[2]),RequestJson.text(b,"reason"));ok(r,"{\"message\":\"Đã cập nhật trạng thái tài khoản\"}");return;}
     String password=PasswordUtil.randomToken().substring(0,10)+"a1",email=RequestJson.text(b,"email");long id=dao.create(RequestJson.text(b,"username"),email,RequestJson.text(b,"fullName"),RequestJson.text(b,"phone"),PasswordUtil.encode(password),RequestJson.text(b,"territory"),RequestJson.strings(b,"roles"),RequestJson.strings(b,"warehouses"));EmailService.activation(email,password);created(r,"{\"id\":"+id+",\"message\":\"Đã tạo tài khoản và gửi thông tin kích hoạt\"}");
   }catch(Exception e){error(r,e.getMessage()!=null&&e.getMessage().contains("duplicate key")?409:400,e.getMessage());}
 }
 protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{
   try{long id=Long.parseLong(q.getPathInfo().substring(1));String b=body(q);long actor=((Number)q.getSession().getAttribute("userId")).longValue();dao.updateUser(id,RequestJson.text(b,"fullName"),RequestJson.text(b,"phone"),RequestJson.text(b,"territory"),RequestJson.strings(b,"roles"),RequestJson.strings(b,"warehouses"),actor);ok(r,"{\"message\":\"Đã cập nhật tài khoản\"}");}catch(Exception e){error(r,400,e.getMessage());}
 }
 private int intParam(HttpServletRequest q,String name,int fallback){try{return Integer.parseInt(q.getParameter(name));}catch(Exception e){return fallback;}}
}
