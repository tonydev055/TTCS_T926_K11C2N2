package controller;

import db.*;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import service.*;
import util.*;

@WebServlet("/api/profile/*")
@MultipartConfig(maxFileSize = 2097152, maxRequestSize = 2200000)
public class HoSoServlet extends CoSoServlet {

    private long actor(HttpServletRequest q) {
        return ((Number) q.getSession().getAttribute("userId")).longValue();
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try (Connection c = KetNoiDB.getConnection()) {
            String path = q.getPathInfo();
            
            var row = TruyVanDB.one(
                c,
                "SELECT id,username,email,full_name,phone,territory,false AS has_avatar FROM users WHERE id=?",
                actor(q)
            );
            row.put("roles", new NguoiDungDB().roles(actor(q)));
            row.put("warehouses", new NguoiDungDB().warehouses(actor(q)));
            PhanHoiJsonUtil.send(r, 200, XuLyJson.stringify(row));
        } catch (Exception e) {
            error(r, 400, DanhMucService.error(e));
        }
    }

    protected void doPut(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try (Connection c = KetNoiDB.getConnection()) {
            var data = XuLyJson.object(body(q));
            if (
                !Set.of("full_name", "phone").containsAll(data.keySet())
            ) throw new IllegalArgumentException("Chỉ được sửa họ tên và số điện thoại");
            String name = DanhMucService.text(data, "full_name", true, 150),
                phone = DanhMucService.phone(DanhMucService.text(data, "phone", true, 20), true);
            TruyVanDB.update(
                c,
                "UPDATE users SET full_name=?,phone=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                name,
                phone,
                actor(q)
            );
            q.getSession().setAttribute("fullName", name);
            ok(r, "{\"message\":\"Đã cập nhật hồ sơ\"}");
        } catch (Exception e) {
            error(r, 400, DanhMucService.error(e));
        }
    }


}
