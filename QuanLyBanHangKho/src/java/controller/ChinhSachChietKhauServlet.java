package controller;

import db.ChinhSachChietKhauDB;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import util.XuLyJson;
import service.ChinhSachChietKhauService;

@WebServlet("/api/discount-policies/*")
public class ChinhSachChietKhauServlet extends CoSoServlet {
    private final ChinhSachChietKhauDB db=new ChinhSachChietKhauDB();
    private Long id(HttpServletRequest q) {
        String path=q.getPathInfo();
        return path==null || path.equals("/") ? null : ChinhSachChietKhauService.positiveId(path.substring(1));
    }
    private void failure(HttpServletResponse r,Exception e) throws IOException {
        if (e instanceof NoSuchElementException) error(r,404,e.getMessage());
        else if (e instanceof IllegalArgumentException) error(r,400,e.getMessage());
        else if (e instanceof SQLException sql && "23503".equals(sql.getSQLState())) error(r,400,"SKU hoặc nhóm hàng không tồn tại");
        else { log("Discount policy request failed",e); error(r,500,"Không thể xử lý chính sách chiết khấu"); }
    }
    protected void doGet(HttpServletRequest q,HttpServletResponse r) throws IOException {
        try {
            if ("/masters".equals(q.getPathInfo())) { ok(r,XuLyJson.stringify(db.masters())); return; }
            Long id=id(q); var all=db.findAll();
            if (id==null) ok(r,XuLyJson.stringify(all));
            else ok(r,XuLyJson.stringify(all.stream().filter(p -> ((Number)p.get("id")).longValue()==id).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chính sách"))));
        } catch (Exception e) { failure(r,e); }
    }
    protected void doPost(HttpServletRequest q,HttpServletResponse r) throws IOException {
        try {
            var input=XuLyJson.object(body(q));
            if ("/quote".equals(q.getPathInfo())) { ok(r,XuLyJson.stringify(db.quote(input))); return; }
            if (id(q)!=null) throw new IllegalArgumentException("Đường dẫn không hợp lệ");
            created(r,XuLyJson.stringify(Map.of("id",db.save(null,input))));
        } catch (Exception e) { failure(r,e); }
    }
    protected void doPut(HttpServletRequest q,HttpServletResponse r) throws IOException {
        try {
            Long id=id(q); if (id==null) throw new IllegalArgumentException("Thiếu ID chính sách");
            ok(r,XuLyJson.stringify(Map.of("id",db.save(id,XuLyJson.object(body(q))))));
        } catch (Exception e) { failure(r,e); }
    }
    protected void doDelete(HttpServletRequest q,HttpServletResponse r) throws IOException {
        try {
            Long id=id(q); if (id==null) throw new IllegalArgumentException("Thiếu ID chính sách");
            if (!db.delete(id)) throw new NoSuchElementException("Không tìm thấy chính sách");
            ok(r,"{\"deleted\":true}");
        } catch (Exception e) { failure(r,e); }
    }
}
