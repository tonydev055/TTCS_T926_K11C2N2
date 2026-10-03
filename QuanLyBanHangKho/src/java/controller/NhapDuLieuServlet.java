package controller;

import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.*;
import service.*;
import util.*;

@WebServlet(urlPatterns = { "/api/imports/*", "/api/templates/*" })
@MultipartConfig(maxFileSize = 8388608, maxRequestSize = 8500000)
public class NhapDuLieuServlet extends CoSoServlet {

    private record Preview(
        String token,
        String kind,
        List<TepExcelService.Row> rows,
        long expires,
        boolean cost
    ) {}

    @SuppressWarnings("unchecked")
    private boolean cost(HttpServletRequest q) {
        return ((List<String>) q.getSession().getAttribute("roles")).contains("SALES_MANAGER");
    }

    protected void doGet(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            if (!q.getServletPath().equals("/api/templates")) throw new IllegalArgumentException(
                "Đường dẫn không hợp lệ"
            );
            String kind = q.getPathInfo().substring(1);
            byte[] bytes = TepExcelService.template(NhapDuLieuService.headers(kind, cost(q)));
            r.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            r.setHeader(
                "Content-Disposition",
                "attachment; filename=\"" + kind + "-template.xlsx\""
            );
            r.getOutputStream().write(bytes);
        } catch (Exception e) {
            error(r, 400, DanhMucService.error(e));
        }
    }

    protected void doPost(HttpServletRequest q, HttpServletResponse r) throws IOException {
        try {
            if (!q.getServletPath().equals("/api/imports")) throw new IllegalArgumentException(
                "Đường dẫn không hợp lệ"
            );
            String[] path = q.getPathInfo().split("/");
            if (
                path.length != 3 || !Set.of("preview", "commit").contains(path[2])
            ) throw new IllegalArgumentException("Đường dẫn không hợp lệ");
            String kind = path[1];
            NhapDuLieuService.headers(kind, cost(q));
            HttpSession session = q.getSession();
            synchronized (session) {
                boolean commit = path[2].equals("commit");
                List<TepExcelService.Row> rows;
                String token;
                if (commit) {
                    var data = XuLyJson.object(body(q));
                    Object saved = session.getAttribute("sprint2Import");
                    if (
                        !(saved instanceof Preview p) ||
                        !p.token.equals(data.get("token")) ||
                        !p.kind.equals(kind) ||
                        p.expires < System.currentTimeMillis() ||
                        p.cost != cost(q)
                    ) throw new IllegalArgumentException(
                        "Bản xem trước hết hạn hoặc đã nhập. Hãy tải tệp lại"
                    );
                    rows = p.rows;
                    token = p.token;
                    session.removeAttribute("sprint2Import");
                } else {
                    Part file = q.getPart("file");
                    if (
                        file == null ||
                        file.getSubmittedFileName() == null ||
                        !file.getSubmittedFileName().toLowerCase(Locale.ROOT).endsWith(".xlsx")
                    ) throw new IllegalArgumentException("Hãy chọn tệp Excel .xlsx theo mẫu");
                    rows = TepExcelService.read(
                        file.getInputStream(),
                        NhapDuLieuService.headers(kind, cost(q))
                    );
                    token = UUID.randomUUID().toString();
                }
                try (Connection c = KetNoiDB.getConnection()) {
                    DanhMucService.begin(c, ((Number) session.getAttribute("userId")).longValue());
                    try {
                        var results = new NhapDuLieuService().process(c, kind, rows, cost(q), commit);
                        if (commit) c.commit();
                        else {
                            c.rollback();
                            List<TepExcelService.Row> confirmedRows = new ArrayList<>();
                            for (int i = 0; i < rows.size(); i++) {
                                var row = rows.get(i);
                                var result = results.get(i);
                                confirmedRows.add(
                                    new TepExcelService.Row(
                                        row.number(),
                                        row.values(),
                                        Boolean.TRUE.equals(result.get("valid"))
                                            ? ""
                                            : String.valueOf(result.get("message"))
                                    )
                                );
                            }
                            session.setAttribute(
                                "sprint2Import",
                                new Preview(
                                    token,
                                    kind,
                                    confirmedRows,
                                    System.currentTimeMillis() + 15 * 60 * 1000,
                                    cost(q)
                                )
                            );
                        }
                        long valid = results
                            .stream()
                            .filter(x -> Boolean.TRUE.equals(x.get("valid")))
                            .count();
                        PhanHoiJsonUtil.send(
                            r,
                            200,
                            XuLyJson.stringify(
                                Map.of(
                                    "token",
                                    token,
                                    "rows",
                                    results,
                                    "total",
                                    results.size(),
                                    "valid",
                                    valid,
                                    "invalid",
                                    results.size() - valid,
                                    "committed",
                                    commit
                                )
                            )
                        );
                    } catch (Exception e) {
                        c.rollback();
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            error(r, 400, DanhMucService.error(e));
        }
    }
}
