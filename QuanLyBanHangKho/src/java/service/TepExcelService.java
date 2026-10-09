package service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Bounded XLSX reader for value-only import templates; never evaluates Excel formulas. */
public final class TepExcelService {

    public record Row(int number, Map<String, Object> values, String error) {}

    private static Document xml(byte[] bytes) throws Exception {
        if (bytes == null) throw new IllegalArgumentException("Thiếu thành phần trong tệp Excel");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private static String text(Element node, String tag) {
        NodeList list = node.getElementsByTagNameNS("*", tag);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < list.getLength(); i++) out.append(list.item(i).getTextContent());
        return out.toString();
    }

    public static List<Row> read(InputStream input, List<String> expected) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        long expanded = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (++entryCount > 300) throw new IllegalArgumentException(
                    "Tệp Excel quá phức tạp"
                );
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 32 * 1024 * 1024) throw new IllegalArgumentException(
                        "Tệp Excel giải nén vượt 32 MB"
                    );
                    out.write(buffer, 0, n);
                }
                if (
                    entries.put(e.getName(), out.toByteArray()) != null
                ) throw new IllegalArgumentException("Tệp Excel có thành phần trùng");
            }
        }
        Document book = xml(entries.get("xl/workbook.xml"));
        NodeList sheets = book.getElementsByTagNameNS("*", "sheet");
        if (sheets.getLength() == 0) throw new IllegalArgumentException("Không có trang tính");
        Element sheet = (Element) sheets.item(0);
        String rel = sheet.getAttributeNS(
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
            "id"
        );
        String target = null;
        NodeList relations = xml(entries.get("xl/_rels/workbook.xml.rels")).getElementsByTagNameNS(
            "*",
            "Relationship"
        );
        for (int i = 0; i < relations.getLength(); i++) {
            Element link = (Element) relations.item(i);
            if (
                link.getAttribute("Id").equals(rel) &&
                !link.getAttribute("TargetMode").equals("External")
            ) target = link.getAttribute("Target");
        }
        if (target == null || target.contains("..")) throw new IllegalArgumentException(
            "Trang tính không hợp lệ"
        );
        String sheetPath = target.startsWith("/") ? target.substring(1) : "xl/" + target;
        List<String> shared = new ArrayList<>();
        if (entries.containsKey("xl/sharedStrings.xml")) {
            NodeList strings = xml(entries.get("xl/sharedStrings.xml")).getElementsByTagNameNS(
                "*",
                "si"
            );
            for (int i = 0; i < strings.getLength(); i++) shared.add(
                text((Element) strings.item(i), "t")
            );
        }
        NodeList rows = xml(entries.get(sheetPath)).getElementsByTagNameNS("*", "row");
        if (rows.getLength() > 5001) throw new IllegalArgumentException(
            "Mỗi lần nhập tối đa 5.000 dòng"
        );
        List<Row> out = new ArrayList<>();
        List<String> headers = null;
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            int number = row.hasAttribute("r") ? Integer.parseInt(row.getAttribute("r")) : i + 1;
            Map<Integer, String> cells = new TreeMap<>();
            String error = "";
            NodeList nodes = row.getElementsByTagNameNS("*", "c");
            for (int j = 0; j < nodes.getLength(); j++) {
                Element cell = (Element) nodes.item(j);
                String ref = cell.getAttribute("r").replaceAll("[0-9]", "");
                int col = 0;
                for (char ch : ref.toCharArray()) col = col * 26 + (ch - 'A' + 1);
                if (col < 1 || col > 40) throw new IllegalArgumentException(
                    "Chỉ hỗ trợ tối đa 40 cột"
                );
                String type = cell.getAttribute("t");
                String value = type.equals("inlineStr") ? text(cell, "t") : text(cell, "v");
                if (type.equals("s")) {
                    int index = Integer.parseInt(value);
                    if (index < 0 || index >= shared.size()) throw new IllegalArgumentException(
                        "Chuỗi Excel không hợp lệ"
                    );
                    value = shared.get(index);
                }
                if (cell.getElementsByTagNameNS("*", "f").getLength() > 0) error =
                    "Không nhập công thức; hãy dán giá trị";
                if (value.length() > 3000) error = "Giá trị ô quá dài";
                cells.put(col - 1, value.trim());
            }
            if (cells.values().stream().allMatch(String::isBlank)) continue;
            if (headers == null) {
                headers = new ArrayList<>();
                for (int col = 0; col <= Collections.max(cells.keySet()); col++) headers.add(
                    cells.getOrDefault(col, "")
                );
                if (!headers.equals(expected)) throw new IllegalArgumentException(
                    "Tiêu đề không đúng mẫu. Hãy tải tệp mẫu mới và giữ nguyên thứ tự cột"
                );
                if (!error.isEmpty()) throw new IllegalArgumentException(error);
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            for (int col = 0; col < headers.size(); col++) data.put(
                headers.get(col),
                cells.getOrDefault(col, "")
            );
            if (
                cells
                    .keySet()
                    .stream()
                    .anyMatch(col -> col >= expected.size())
            ) error = "Dòng có cột ngoài mẫu";
            out.add(new Row(number, data, error));
        }
        if (headers == null || out.isEmpty()) throw new IllegalArgumentException(
            "Tệp chưa có dòng dữ liệu"
        );
        return out;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    public static byte[] template(List<String> headers) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) {
            put(
                z,
                "[Content_Types].xml",
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>"
            );
            put(
                z,
                "_rels/.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>"
            );
            put(
                z,
                "xl/workbook.xml",
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"NhapDuLieu\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
            );
            put(
                z,
                "xl/_rels/workbook.xml.rels",
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>"
            );
            StringBuilder row = new StringBuilder(
                "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><sheetData><row r=\"1\">"
            );
            for (int i = 0; i < headers.size(); i++) row.append("<c r=\"")
                .append((char) ('A' + i))
                .append("1\" t=\"inlineStr\"><is><t>")
                .append(esc(headers.get(i)))
                .append("</t></is></c>");
            row.append("</row></sheetData></worksheet>");
            put(z, "xl/worksheets/sheet1.xml", row.toString());
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream z, String path, String content) throws IOException {
        z.putNextEntry(new ZipEntry(path));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
