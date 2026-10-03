package controller;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import util.PhanHoiJsonUtil;

public abstract class CoSoServlet extends HttpServlet {

    protected void ok(HttpServletResponse r, String json) throws IOException {
        PhanHoiJsonUtil.send(r, 200, json);
    }

    protected void created(HttpServletResponse r, String json) throws IOException {
        PhanHoiJsonUtil.send(r, 201, json);
    }

    protected void error(HttpServletResponse r, int s, String m) throws IOException {
        PhanHoiJsonUtil.send(r, s, "{\"message\":\"" + PhanHoiJsonUtil.esc(m) + "\"}");
    }

    protected String body(HttpServletRequest q) throws IOException {
        q.setCharacterEncoding("UTF-8");
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[8192];
        int size;
        var reader = q.getReader();
        while ((size = reader.read(buffer)) != -1) {
            if (out.length() + size > 2_000_000) throw new IOException("Dữ liệu yêu cầu quá lớn");
            out.append(buffer, 0, size);
        }
        return out.toString();
    }

    protected String jsonRows(List<Map<String, Object>> rows) {
        return util.XuLyJson.stringify(rows);
    }
}
