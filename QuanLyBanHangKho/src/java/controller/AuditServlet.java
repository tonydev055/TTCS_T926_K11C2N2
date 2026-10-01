package controller;
import dao.Sql;
import service.CatalogService;
import util.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
@WebServlet("/api/audit/*")
public class AuditServlet extends BaseServlet {
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try(Connection c=DBConnection.getConnection()){
   if("/filters".equals(q.getPathInfo())){JsonUtil.send(r,200,Json.stringify(Map.of("users",Sql.rows(c,"SELECT id,full_name FROM users ORDER BY full_name"),"types",Sql.rows(c,"SELECT DISTINCT object_type FROM audit_logs ORDER BY object_type"))));return;}
   List<Object> args=new ArrayList<>();String where=" WHERE 1=1";
   if(q.getParameter("actor")!=null&&!q.getParameter("actor").isBlank()){where+=" AND a.actor_user_id=?";args.add(Long.parseLong(q.getParameter("actor")));}
   if(q.getParameter("type")!=null&&!q.getParameter("type").isBlank()){where+=" AND a.object_type=?";args.add(q.getParameter("type"));}
   if(q.getParameter("from")!=null&&!q.getParameter("from").isBlank()){where+=" AND a.created_at>=?";args.add(java.sql.Date.valueOf(LocalDate.parse(q.getParameter("from"))));}
   if(q.getParameter("to")!=null&&!q.getParameter("to").isBlank()){where+=" AND a.created_at<?";args.add(java.sql.Date.valueOf(LocalDate.parse(q.getParameter("to")).plusDays(1)));}
   long total=((Number)Sql.one(c,"SELECT count(*) total FROM audit_logs a"+where,args.toArray()).get("total")).longValue();
   int page=Math.max(1,Integer.parseInt(Objects.toString(q.getParameter("page"),"1")));args.add(50);args.add((page-1)*50);
   var rows=Sql.rows(c,"SELECT a.*,u.full_name actor_name FROM audit_logs a LEFT JOIN users u ON u.id=a.actor_user_id"+where+" ORDER BY a.id DESC LIMIT ? OFFSET ?",args.toArray());
   @SuppressWarnings("unchecked") List<String> roles=(List<String>)q.getSession().getAttribute("roles");
   for(var row:rows)for(String key:List.of("old_value","new_value")){Object value=row.get(key);if(value!=null){try{var data=Json.object(value.toString());if(!roles.contains("SALES_MANAGER"))data.remove("cost_price");row.put(key,data);}catch(IllegalArgumentException ignored){row.put(key,"Nội dung nhật ký cũ");}}}
   JsonUtil.send(r,200,Json.stringify(Map.of("items",rows,"total",total,"page",page,"size",50)));
  }catch(Exception e){error(r,400,CatalogService.error(e));}
 }
}
