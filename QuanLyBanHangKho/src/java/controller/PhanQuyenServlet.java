package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import security.PhanQuyen;
import util.XuLyJson;

@WebServlet("/api/role-permissions")
public class PhanQuyenServlet extends CoSoServlet {

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        writeMatrix(r);
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            String content = body(q);
            String role = util.YeuCauJson.text(content, "role");
            List<String> permissions = util.YeuCauJson.strings(content, "permissions");
            long actor = ((Number) q.getSession(false).getAttribute("userId")).longValue();
            PhanQuyen.updateRole(role, permissions, actor);
            writeMatrix(r);
        } catch (IllegalArgumentException e) {
            error(r, 400, e.getMessage());
        } catch (Exception e) {
            error(r, 500, "Không thể lưu cấu hình phân quyền");
        }
    }

    private void writeMatrix(HttpServletResponse r) throws IOException {
        Map<String, List<String>> matrix = PhanQuyen.matrix();
        Set<String> permissionSet = new TreeSet<>();
        permissionSet.addAll(PhanQuyen.knownPermissions());
        ok(
            r,
            XuLyJson.stringify(
                Map.of("roles", matrix.keySet(), "permissions", permissionSet, "matrix", matrix)
            )
        );
    }
}
