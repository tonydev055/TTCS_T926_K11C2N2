package controller;
import jakarta.servlet.annotation.WebServlet;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import service.DoiChieuCongNoService;import util.XuLyJson;
@WebServlet({"/api/reconciliations/*","/api/dealer-reconciliations/*"})
public final class DoiChieuCongNoServlet extends TaiChinhServlet {
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{boolean dealer=q.getServletPath().equals("/api/dealer-reconciliations");String p=path(q);var s=new DoiChieuCongNoService();if(p.equals("/"))send(r,s.list(user(q),dealer));else if(p.matches("/[1-9][0-9]*"))send(r,s.detail(user(q),dealer,id(p.substring(1))));else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}}
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{try{checkWrite(q);boolean dealer=q.getServletPath().equals("/api/dealer-reconciliations");var s=new DoiChieuCongNoService();var in=XuLyJson.object(body(q));String p=path(q);if(!dealer&&p.equals("/"))created(r,XuLyJson.stringify(s.create(user(q),in)));else if(dealer&&p.matches("/[1-9][0-9]*/response"))created(r,XuLyJson.stringify(s.respond(user(q),id(p.split("/")[1]),in)));else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}}
}
