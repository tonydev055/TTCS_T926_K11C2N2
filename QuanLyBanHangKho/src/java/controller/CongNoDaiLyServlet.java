package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.CongNoDaiLyService;
@WebServlet("/api/dealer-debt/*")
public final class CongNoDaiLyServlet extends TaiChinhServlet {
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try{String p=path(q);var service=new CongNoDaiLyService();if(p.equals("/"))send(r,service.list(user(q)));else if(p.matches("/[1-9][0-9]*"))send(r,service.detail(user(q),id(p.substring(1))));else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}
 }
}
