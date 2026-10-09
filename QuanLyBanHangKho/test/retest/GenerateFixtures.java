import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import service.TepExcelService;
import service.NhapDuLieuService;
public class GenerateFixtures {
 static String row(int n,String... values){StringBuilder s=new StringBuilder("<row r=\""+n+"\">");for(int i=0;i<values.length;i++)s.append("<c r=\"").append((char)('A'+i)).append(n).append("\" t=\"inlineStr\"><is><t>").append(values[i]).append("</t></is></c>");return s.append("</row>").toString();}
 static void file(Path dir,String name,String kind,boolean cost,String rows)throws Exception{byte[] template=TepExcelService.template(NhapDuLieuService.headers(kind,cost));try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(template));ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(dir.resolve(name)))){ZipEntry e;while((e=in.getNextEntry())!=null){byte[] data=in.readAllBytes();if(e.getName().equals("xl/worksheets/sheet1.xml"))data=new String(data,StandardCharsets.UTF_8).replace("</sheetData>",rows+"</sheetData>").getBytes(StandardCharsets.UTF_8);out.putNextEntry(new ZipEntry(e.getName()));out.write(data);out.closeEntry();}}}
 public static void main(String[] args)throws Exception{Path dir=Path.of(args[0]);String p=args[1];
 file(dir,"users.xlsx","users",false,row(2,p+"excel",p+"excel@example.test","Nguyễn Excel","0901234567","CUSTOMER","", "Hà Nội")+row(3,p+"excelbad",p+"excelbad@example.test","Lỗi kho","0901234567","WAREHOUSE","", ""));
 file(dir,"products.xlsx","products",true,row(2,p+"EXCEL","Sản phẩm Excel",p.toUpperCase()+"C","lon","24 lon","true","13000")+row(3,p+"EXCELBAD","Lỗi nhóm","MISSING","lon","","true","1"));
 file(dir,"formula.xlsx","products",true,"<row r=\"2\"><c r=\"A2\"><f>1+1</f><v>2</v></c></row>");
 ImageIO.write(new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB),"jpg",dir.resolve("sample.jpg").toFile());
 }
}
