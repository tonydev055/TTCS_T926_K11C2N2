package controller;
import jakarta.servlet.http.*;
import util.JsonUtil;
import java.io.*;
import java.util.*;
public abstract class BaseServlet extends HttpServlet {
    protected void ok(HttpServletResponse r,String json)throws IOException{JsonUtil.send(r,200,json);}
    protected void created(HttpServletResponse r,String json)throws IOException{JsonUtil.send(r,201,json);}
    protected void error(HttpServletResponse r,int s,String m)throws IOException{JsonUtil.send(r,s,"{\"message\":\""+JsonUtil.esc(m)+"\"}");}
    protected String body(HttpServletRequest q)throws IOException{return q.getReader().lines().reduce("",(a,b)->a+b);}
    protected String jsonRows(List<Map<String,Object>> rows){
        StringBuilder b=new StringBuilder("["); boolean first=true;
        for(var row:rows){if(!first)b.append(',');first=false;b.append('{');boolean f=true;
            for(var e:row.entrySet()){if(!f)b.append(',');f=false;b.append('"').append(JsonUtil.esc(e.getKey())).append("\":");
                Object v=e.getValue(); if(v==null)b.append("null"); else if(v instanceof Number||v instanceof Boolean)b.append(v); else b.append('"').append(JsonUtil.esc(String.valueOf(v))).append('"');}
            b.append('}');} return b.append(']').toString();
    }
}
