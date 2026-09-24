package controller;
import dao.PriceListDAO; import jakarta.servlet.annotation.WebServlet; import jakarta.servlet.http.*; import java.io.*;
@WebServlet("/api/price-lists/*")
public class PriceListsServlet extends BaseServlet {
 private final PriceListDAO dao=new PriceListDAO();
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{
   try{String p=q.getPathInfo(); if(p==null||p.equals("/")){ok(r,jsonRows(dao.findAll()));return;}
       long id=Long.parseLong(p.substring(1)); var row=dao.findById(id); if(row==null){error(r,404,"Không tìm thấy dữ liệu");return;}
       ok(r,jsonRows(java.util.List.of(row)));
   }catch(Exception e){error(r,500,e.getMessage());}
 }
 protected void doDelete(HttpServletRequest q,HttpServletResponse r)throws IOException{
   try{long id=Long.parseLong(q.getPathInfo().substring(1));dao.delete(id);ok(r,"{\"deleted\":true}");}
   catch(Exception e){error(r,400,e.getMessage());}
 }
}
