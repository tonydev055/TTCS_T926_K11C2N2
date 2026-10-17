package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.PhieuThuService;
@WebServlet("/api/cash-receipts/*")
public final class PhieuThuServlet extends TaiChinhServlet {
 @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
  try{String p=path(q);var service=new PhieuThuService();if(p.equals("/"))send(r,service.list());else if(p.matches("/[1-9][0-9]*"))send(r,service.detail(id(p.substring(1))));else throw new NoSuchElementException("Không tìm thấy phiếu thu");}catch(Exception e){fail(r,e);}
 }
 @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{error(r,405,"Ghi nhận khoản thu và đối trừ trong chức năng Thanh toán và đối trừ trước khi in phiếu");}
}
