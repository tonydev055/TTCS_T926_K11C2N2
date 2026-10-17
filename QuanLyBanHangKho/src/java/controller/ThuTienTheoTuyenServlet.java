package controller;
import jakarta.servlet.annotation.*;import jakarta.servlet.http.*;import java.io.IOException;import java.util.*;import service.ThuTienTheoTuyenService;
@WebServlet({"/api/route-collections/*","/api/collection-settlements/*"})
@MultipartConfig(maxFileSize=2097152,maxRequestSize=3145728)
public final class ThuTienTheoTuyenServlet extends TaiChinhServlet {
 protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{try{boolean accountant=q.getServletPath().equals("/api/collection-settlements");var service=new ThuTienTheoTuyenService();String p=path(q);if(p.equals("/"))send(r,service.list(user(q),accountant));else if(p.matches("/[1-9][0-9]*/proof")){byte[] image=service.proof(user(q),accountant,id(p.split("/")[1]));r.setContentType("image/png");r.setHeader("Cache-Control","private, no-store");r.setHeader("X-Content-Type-Options","nosniff");r.setContentLength(image.length);r.getOutputStream().write(image);}else throw new NoSuchElementException("Không tìm thấy biên lai");}catch(Exception e){fail(r,e);}}
 protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{try{
  boolean accountant=q.getServletPath().equals("/api/collection-settlements");var service=new ThuTienTheoTuyenService();String p=path(q);
  if(accountant&&p.matches("/[1-9][0-9]*/accept")){checkWrite(q);send(r,service.accept(user(q),id(p.split("/")[1])));return;}
  if(!accountant&&p.equals("/")){checkOrigin(q);q.setCharacterEncoding("UTF-8");var in=new LinkedHashMap<String,Object>();for(String field:List.of("customerId","amount","collectedDate","note","requestKey"))in.put(field,q.getParameter(field));Part photo=q.getPart("proof");byte[] image=null;if(photo!=null){if(photo.getSize()>2097152)throw new IllegalArgumentException("Ảnh tối đa 2 MB");try(var stream=photo.getInputStream()){image=stream.readAllBytes();}}send(r,Map.of("id",service.create(user(q),in,image)));return;}
  throw new NoSuchElementException("Không tìm thấy chức năng");
 }catch(IllegalStateException|jakarta.servlet.ServletException e){error(r,400,"Biểu mẫu không hợp lệ hoặc ảnh vượt 2 MB");}catch(Exception e){fail(r,e);}}
}
