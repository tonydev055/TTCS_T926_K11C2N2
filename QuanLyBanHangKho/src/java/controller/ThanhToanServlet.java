package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.ThanhToanService;
import service.KhoService;
import util.XuLyJson;
/** S7-02: chỉ ghi thêm khoản thu hoặc bút toán hoàn tác; không sửa/xóa. */
@WebServlet("/api/payments/*")
public final class ThanhToanServlet extends TaiChinhServlet {
 private final ThanhToanService service=new ThanhToanService();
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException {
  try{String p=path(q);if(p.equals("/"))send(r,service.list());else if(p.matches("/[1-9][0-9]*"))send(r,service.detail(id(p.substring(1))));else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}
 }
 @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException {
  try{checkWrite(q);var in=XuLyJson.object(body(q));String p=path(q);
   if(p.equals("/"))created(r,XuLyJson.stringify(service.create(user(q),in)));
   else if(p.matches("/[1-9][0-9]*/reversals"))created(r,XuLyJson.stringify(service.reverse(id(p.split("/")[1]),user(q),KhoService.text(in.get("reason"),"Lý do",1000,true))));
   else throw new NoSuchElementException("Không tìm thấy chức năng");
  }catch(Exception e){fail(r,e);}
 }
 @Override protected void doDelete(HttpServletRequest q,HttpServletResponse r)throws IOException{error(r,405,"Khoản thu đã ghi nhận không được xóa; hãy lập bút toán điều chỉnh");}
 @Override protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{error(r,405,"Khoản thu đã ghi nhận không được sửa; hãy lập bút toán điều chỉnh");}
}
