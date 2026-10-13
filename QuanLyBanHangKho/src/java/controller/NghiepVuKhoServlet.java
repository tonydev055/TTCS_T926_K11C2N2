package controller;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import util.XuLyJson;
/** Tiện ích HTTP module kho Sprint 5; danh tính luôn lấy từ phiên server. */
public abstract class NghiepVuKhoServlet extends CoSoServlet {
    protected long user(HttpServletRequest q){return ((Number)q.getSession(false).getAttribute("userId")).longValue();}
    @SuppressWarnings("unchecked") protected List<String> roles(HttpServletRequest q){return (List<String>)q.getSession(false).getAttribute("roles");}
    protected String path(HttpServletRequest q){return Objects.toString(q.getPathInfo(),"/");}
    protected long id(String raw){try{return service.KhoService.id(Long.parseLong(raw));}catch(RuntimeException e){throw new IllegalArgumentException("Mã không hợp lệ");}}
    protected void send(HttpServletResponse r,Object value)throws IOException{ok(r,XuLyJson.stringify(value));}
    protected void fail(HttpServletResponse r,Exception e)throws IOException{
        if(e instanceof SecurityException)error(r,403,e.getMessage());
        else if(e instanceof NoSuchElementException)error(r,404,e.getMessage());
        else if(e instanceof IllegalArgumentException||e instanceof IOException)error(r,400,e.getMessage());
        else if(e instanceof SQLException s&&("23505".equals(s.getSQLState())||"23503".equals(s.getSQLState())))error(r,409,"Mã bị trùng hoặc dữ liệu đang được sử dụng");
        else{log("Lỗi nghiệp vụ kho",e);error(r,500,"Không thể xử lý nghiệp vụ kho, vui lòng thử lại");}
    }
}
