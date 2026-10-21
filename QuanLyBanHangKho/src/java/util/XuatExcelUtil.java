package util;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Xuất XLSX bằng OOXML, chuỗi là inlineStr để không thực thi công thức do dữ liệu nhập. */
public final class XuatExcelUtil {
 private XuatExcelUtil(){}
 private static String xml(Object x){return Objects.toString(x,"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","");}
 public static byte[] workbook(List<String> headers,List<List<Object>> rows)throws IOException{
  var out=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
   add(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
   add(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
   add(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Cong no\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
   add(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
   var sheet=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
   var all=new ArrayList<List<?>>();all.add(headers);all.addAll(rows);
   for(var row:all){sheet.append("<row>");for(Object value:row){if(value instanceof Number)sheet.append("<c t=\"n\"><v>").append(xml(value)).append("</v></c>");else sheet.append("<c t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value)).append("</t></is></c>");}sheet.append("</row>");}
   add(zip,"xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
  }return out.toByteArray();
 }
 private static void add(ZipOutputStream zip,String path,String text)throws IOException{zip.putNextEntry(new ZipEntry(path));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
