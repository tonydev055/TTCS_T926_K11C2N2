<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %><%
// URL cũ chỉ chuyển hướng. Mọi dữ liệu đi qua /api để kiểm tra phiên server và quyền.
response.setHeader("Cache-Control","no-store");
if ("data".equalsIgnoreCase(request.getParameter("action"))) {
 StringBuilder query=new StringBuilder();
 for(String key:new String[]{"from","to","groupBy","compare"}){
  String value=request.getParameter(key);if(value!=null){if(query.length()>0)query.append('&');query.append(key).append('=').append(java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8));}
 }
 response.sendRedirect(response.encodeRedirectURL("api/sales-report"+(query.length()>0?"?"+query:"")));
} else {
 response.sendRedirect(response.encodeRedirectURL("index.html#"+java.net.URLEncoder.encode("Báo cáo doanh số",java.nio.charset.StandardCharsets.UTF_8).replace("+","%20")));
}
%>
