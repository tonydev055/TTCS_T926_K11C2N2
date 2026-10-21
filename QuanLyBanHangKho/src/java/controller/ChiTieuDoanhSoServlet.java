package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.ChiTieuDoanhSoService;
import util.XuLyJson;
/** GET /api/sales-targets?month=&staffId=&region=; GET /{id}/history. POST/PUT: targetAmount,note,reason; POST thêm month,staffId,region; PUT thêm version. */
@WebServlet("/api/sales-targets/*")
public final class ChiTieuDoanhSoServlet extends TaiChinhServlet {
 private final ChiTieuDoanhSoService service=new ChiTieuDoanhSoService();
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
  String[] p=path(q).split("/");
  if(path(q).equals("/"))send(r,service.list(user(q),roles(q),q.getParameter("month"),q.getParameter("staffId"),q.getParameter("region")));
  else if(p.length==3&&p[2].equals("history"))send(r,Map.of("items",service.history(id(p[1]),user(q),roles(q))));
  else throw new NoSuchElementException("Không tìm thấy chức năng chỉ tiêu");
 }catch(Exception e){fail(r,e);}}
 @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{write(q,r,false);}
 @Override protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{write(q,r,true);}
 private void write(HttpServletRequest q,HttpServletResponse r,boolean update)throws IOException{try{
  checkWrite(q);String[] p=path(q).split("/");if(update?p.length!=2:!path(q).equals("/"))throw new NoSuchElementException("Không tìm thấy chỉ tiêu");
  long result=service.save(update?id(p[1]):null,user(q),roles(q),XuLyJson.object(body(q)));send(r,Map.of("id",result));
 }catch(Exception e){fail(r,e);}}
}
