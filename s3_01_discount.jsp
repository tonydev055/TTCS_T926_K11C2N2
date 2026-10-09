<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%
response.sendRedirect(request.getContextPath()+"/index.html#"+java.net.URLEncoder.encode("Chính sách chiết khấu","UTF-8").replace("+","%20"));
%>
