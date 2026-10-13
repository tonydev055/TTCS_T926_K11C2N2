package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import util.XuLyJson;
import service.KiemKeKhoService;
@WebServlet("/api/stock-counts/*")
public class KiemKeKhoServlet extends NghiepVuKhoServlet {
    private final KiemKeKhoService service=new KiemKeKhoService();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{if(path(q).equals("/"))send(r,service.list(user(q),roles(q)));else send(r,service.detail(id(path(q).substring(1)),user(q),roles(q)));}catch(Exception e){fail(r,e);}}
    protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{try{var in=XuLyJson.object(body(q));String[] p=path(q).split("/");if(path(q).equals("/")){long id=service.create(user(q),roles(q),in);created(r,XuLyJson.stringify(service.detail(id,user(q),roles(q))));}else if(p.length==3&&p[2].equals("confirm")){service.confirm(id(p[1]),user(q),roles(q),Objects.toString(in.get("reason"),""));send(r,service.detail(id(p[1]),user(q),roles(q)));}else throw new NoSuchElementException("Không tìm thấy chức năng");}catch(Exception e){fail(r,e);}}
    protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{try{long id=id(path(q).substring(1));service.counts(id,user(q),roles(q),XuLyJson.object(body(q)));send(r,service.detail(id,user(q),roles(q)));}catch(Exception e){fail(r,e);}}
}

