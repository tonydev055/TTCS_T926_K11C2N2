package controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import util.XuLyJson;
import service.PhieuNhapKhoService;
import service.KhoService;
/** S5-04: GET list/{id}/masters; POST create/{id}/confirm/{id}/adjustments; PUT {id} hoặc products/{id}/lot-mode. */
@WebServlet("/api/stock-receipts/*")
public class PhieuNhapKhoServlet extends NghiepVuKhoServlet {
    private final PhieuNhapKhoService service=new PhieuNhapKhoService();
    protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
        if(path(q).equals("/masters"))send(r,service.masters(user(q),roles(q)));
        else if(path(q).equals("/"))send(r,service.list(user(q),roles(q)));
        else send(r,service.detail(id(path(q).substring(1)),user(q),roles(q)));
    }catch(Exception e){fail(r,e);}}
    private void save(HttpServletRequest q,HttpServletResponse r,boolean update)throws IOException{try{
        var in=XuLyJson.object(body(q));String[] p=path(q).split("/");long result;
        if(update&&p.length==4&&p[1].equals("products")&&p[3].equals("lot-mode")){service.lotMode(id(p[2]),user(q),roles(q),KhoService.active(in.get("lotManaged")));send(r,Map.of("saved",true));return;}
        if(!update&&p.length==3&&p[2].equals("confirm")){service.confirm(id(p[1]),user(q),roles(q));send(r,service.detail(id(p[1]),user(q),roles(q)));return;}
        if(!update&&path(q).equals("/"))result=service.save(null,null,user(q),roles(q),in);
        else if(update&&p.length==2)result=service.save(id(p[1]),null,user(q),roles(q),in);
        else if(!update&&p.length==3&&p[2].equals("adjustments"))result=service.save(null,id(p[1]),user(q),roles(q),in);
        else throw new NoSuchElementException("Không tìm thấy chức năng");
        if(update)send(r,service.detail(result,user(q),roles(q)));else created(r,XuLyJson.stringify(service.detail(result,user(q),roles(q))));
    }catch(Exception e){fail(r,e);}}
    protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{save(q,r,false);}
    protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{save(q,r,true);}
}
