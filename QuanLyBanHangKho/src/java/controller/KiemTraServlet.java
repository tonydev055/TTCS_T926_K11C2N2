package controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;

@WebServlet("/api/health")
public class KiemTraServlet extends CoSoServlet {

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        ok(r, "{\"status\":\"ok\",\"app\":\"QuanLyBanHangKho\"}");
    }
}
