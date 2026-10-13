package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.KhoService;
import util.XuLyJson;
/** S5-03: GET /api/warehouses/config; POST /api/warehouses; PUT /{id}; POST /{id}/locations; PUT /{id}/locations/{locationId}. */
@WebServlet("/api/warehouses/*")
public class KhoServlet extends NghiepVuKhoServlet {
    private final KhoService service=new KhoService();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        var out=service.list(user(q),roles(q));
        if(path(q).equals("/config"))send(r,out);
        else if(path(q).equals("/"))send(r,out.get("warehouses"));
        else throw new NoSuchElementException("Không tìm thấy chức năng");
    }catch(Exception e){fail(r,e);}}
    private void save(HttpServletRequest q,HttpServletResponse r,boolean update)throws IOException{try{
        String[] p=path(q).split("/");var in=XuLyJson.object(body(q));long result;
        if(!update&&path(q).equals("/"))result=service.save(null,user(q),roles(q),in);
        else if(update&&p.length==2)result=service.save(id(p[1]),user(q),roles(q),in);
        else if(!update&&p.length==3&&p[2].equals("locations"))result=service.location(id(p[1]),null,user(q),roles(q),in);
        else if(update&&p.length==4&&p[2].equals("locations"))result=service.location(id(p[1]),id(p[3]),user(q),roles(q),in);
        else throw new NoSuchElementException("Không tìm thấy chức năng");
        if(update)send(r,Map.of("id",result));else created(r,XuLyJson.stringify(Map.of("id",result)));
    }catch(Exception e){fail(r,e);}}
    protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{save(q,r,false);}
    protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{save(q,r,true);}
}
