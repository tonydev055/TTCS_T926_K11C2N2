package service;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

public final class ImageService {
 public record Images(byte[] image,byte[] thumbnail){}
 public static Images normalize(byte[] bytes,boolean avatar)throws IOException{
  if(bytes.length==0||bytes.length>2*1024*1024)throw new IllegalArgumentException("Ảnh JPG/PNG phải có dung lượng tối đa 2 MB");
  try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
   var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Tệp không phải ảnh JPG/PNG");var reader=readers.next();
   try{String format=reader.getFormatName();if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw new IllegalArgumentException("Chỉ chấp nhận JPG/PNG");reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||(long)w*h>20_000_000)throw new IllegalArgumentException("Ảnh quá lớn (tối đa 20 megapixel)");BufferedImage original=reader.read(0);return new Images(resize(original,avatar?256:800,avatar),resize(original,64,true));}finally{reader.dispose();}
  }
 }
 private static byte[] resize(BufferedImage original,int size,boolean square)throws IOException{
  int w=original.getWidth(),h=original.getHeight(),side=Math.min(w,h);int tw=square?size:Math.max(1,(int)(w*Math.min(1.0,size/(double)Math.max(w,h)))),th=square?size:Math.max(1,(int)(h*Math.min(1.0,size/(double)Math.max(w,h))));
  BufferedImage out=new BufferedImage(tw,th,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);if(square)g.drawImage(original,0,0,tw,th,(w-side)/2,(h-side)/2,(w+side)/2,(h+side)/2,null);else g.drawImage(original,0,0,tw,th,null);}finally{g.dispose();}ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(out,"png",bytes);return bytes.toByteArray();
 }
}
