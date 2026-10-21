package service;
import java.util.*;
import java.math.BigDecimal;
import java.io.*;
import java.util.zip.*;
import util.XuatExcelUtil;
public final class ReceivablesIntegrationTest {
 public static void main(String[] args)throws Exception{
  if(!Objects.toString(System.getenv("DB_URL"),"").matches("jdbc:postgresql://localhost:5432/khoflow_s7_test_[0-9]{14}"))throw new IllegalStateException("Chỉ DB tạm");
  var data=new SoCongNoService().aging("DL-S7","","",false);var summary=(Map<?,?>)data.get("summary");
  if(((BigDecimal)summary.get("outstanding")).compareTo(new BigDecimal("700.00"))!=0)throw new AssertionError("Báo cáo phải khớp thanh toán và hoàn tác");
  var empty=new SoCongNoService().aging("khong-ton-tai","","",false);if(!((List<?>)empty.get("dealers")).isEmpty())throw new AssertionError("Không được thêm bản ghi mẫu");
  byte[] bytes=XuatExcelUtil.workbook(List.of("Đại lý","Tiền"),List.of(List.of("=HYPERLINK(\"x\")",new BigDecimal("123.45"))));boolean text=false,number=false;int entries=0;
  try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry item;while((item=zip.getNextEntry())!=null){entries++;String xml=new String(zip.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(item.getName().endsWith("sheet1.xml")){text=xml.contains("inlineStr")&&!xml.contains("<f>");number=xml.contains("<v>123.45</v>");}}}
  if(entries!=5||!text||!number)throw new AssertionError("XLSX không hợp lệ hoặc công thức không an toàn");
  System.out.println("S7-03: số dư, lọc rỗng và XLSX an toàn đều đạt");
 }
}
