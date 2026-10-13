package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import util.XuLyJson;
import service.ChuyenKhoService;
/** GET /api/stock-transfers list/{id}/masters; POST create/{id}/send/{id}/receive. */
@WebServlet("/api/stock-transfers/*")
public class ChuyenKhoServlet extends NghiepVuKhoServlet {
    private final ChuyenKhoService service=new ChuyenKhoService();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        if(path(q).equals("/masters"))send(r,service.masters(user(q),roles(q)));else if(path(q).equals("/"))send(r,service.list(user(q),roles(q)));else send(r,service.detail(id(path(q).substring(1)),user(q),roles(q)));
    }catch(Exception e){fail(r,e);}}
    protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        var in=XuLyJson.object(body(q));String[] p=path(q).split("/");
        if(path(q).equals("/")){long id=service.create(user(q),roles(q),in);created(r,XuLyJson.stringify(service.detail(id,user(q),roles(q))));return;}
        if(p.length!=3)throw new NoSuchElementException("Không tìm thấy chức năng");
        if(p[2].equals("send"))service.send(id(p[1]),user(q),roles(q));else if(p[2].equals("receive"))service.receive(id(p[1]),user(q),roles(q),in);else throw new NoSuchElementException("Không tìm thấy chức năng");
        send(r,service.detail(id(p[1]),user(q),roles(q)));
    }catch(Exception e){fail(r,e);}}
}

