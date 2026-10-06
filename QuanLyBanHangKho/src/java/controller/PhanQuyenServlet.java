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
        Map<String, List<String>> matrix = PhanQuyen.matrix();
        Set<String> permissionSet = new TreeSet<>();
        matrix.values().forEach(permissionSet::addAll);
        ok(
            r,
            XuLyJson.stringify(
                Map.of("roles", matrix.keySet(), "permissions", permissionSet, "matrix", matrix)
            )
        );
    }
}
