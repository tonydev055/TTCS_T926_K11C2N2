package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import service.PhieuSoanService;
import util.XuLyJson;

/** S6-03: tạo phiếu soạn gộp và cập nhật kết quả soạn từng dòng. */
@WebServlet("/api/pick-lists/*")
public class PhieuSoanServlet extends NghiepVuKhoServlet {
    private final PhieuSoanService service = new PhieuSoanService();
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException {
        try { String p=path(q); if(p.equals("/")) send(r,service.list(user(q),roles(q))); else if(p.equals("/orders")) send(r,service.availableOrders(user(q),roles(q))); else send(r,service.detail(id(p.substring(1)),user(q),roles(q))); }
        catch(Exception e){fail(r,e);}
    }
    @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException {
        try { var in=XuLyJson.object(body(q)); long id=service.create(user(q),roles(q),in); created(r,XuLyJson.stringify(service.detail(id,user(q),roles(q)))); }
        catch(Exception e){fail(r,e);}
    }
    @Override protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException {
        try { String[] p=path(q).split("/"); if(p.length!=3||!p[2].equals("lines")) throw new NoSuchElementException("Không tìm thấy chức năng"); service.saveLines(id(p[1]),user(q),roles(q),XuLyJson.object(body(q))); send(r,service.detail(id(p[1]),user(q),roles(q))); }
        catch(Exception e){fail(r,e);}
    }
}
