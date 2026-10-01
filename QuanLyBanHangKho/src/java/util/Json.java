package util;

import java.math.BigDecimal;
import java.util.*;

/** Small strict JSON codec for request objects and nested API responses. */
public final class Json {
 private final String source; private int position;
 private Json(String source){this.source=source;}
 public static Object parse(String source){Json p=new Json(source);Object value=p.value(0);p.space();if(p.position!=source.length())throw invalid();return value;}
 @SuppressWarnings("unchecked") public static Map<String,Object> object(String source){Object x=parse(source);if(!(x instanceof Map))throw invalid();return (Map<String,Object>)x;}
 public static String stringify(Object x){
  if(x==null)return "null";
  if(x instanceof Boolean||x instanceof Number)return x.toString();
  if(x instanceof Map<?,?> m){StringJoiner j=new StringJoiner(",","{","}");m.forEach((k,v)->j.add(stringify(String.valueOf(k))+":"+stringify(v)));return j.toString();}
  if(x instanceof Collection<?> a){StringJoiner j=new StringJoiner(",","[","]");a.forEach(v->j.add(stringify(v)));return j.toString();}
  StringBuilder out=new StringBuilder("\"");for(char ch:String.valueOf(x).toCharArray()){switch(ch){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(ch<32)out.append(String.format("\\u%04x",(int)ch));else out.append(ch);}}}return out.append('"').toString();
 }
 private Object value(int depth){
  if(depth>32)throw invalid();space();if(position>=source.length())throw invalid();char c=source.charAt(position);
  if(c=='"')return string();
  if(c=='{'){position++;Map<String,Object> m=new LinkedHashMap<>();space();if(take('}'))return m;do{space();String k=string();space();expect(':');if(m.containsKey(k))throw invalid();m.put(k,value(depth+1));space();if(take('}'))return m;expect(',');}while(true);}
  if(c=='['){position++;List<Object> a=new ArrayList<>();space();if(take(']'))return a;do{a.add(value(depth+1));space();if(take(']'))return a;expect(',');}while(true);}
  for(String literal:List.of("true","false","null"))if(source.startsWith(literal,position)){position+=literal.length();return literal.equals("null")?null:literal.equals("true");}
  int start=position;while(position<source.length()&&"-+0123456789.eE".indexOf(source.charAt(position))>=0)position++;
  String token=source.substring(start,position);if(!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")||token.length()>80)throw invalid();
  try{return new BigDecimal(token);}catch(NumberFormatException e){throw invalid();}
 }
 private String string(){expect('"');StringBuilder s=new StringBuilder();while(position<source.length()){char c=source.charAt(position++);if(c=='"')return s.toString();if(c<32)throw invalid();if(c=='\\'){if(position>=source.length())throw invalid();c=source.charAt(position++);switch(c){case '"','\\','/'->s.append(c);case 'b'->s.append('\b');case 'f'->s.append('\f');case 'n'->s.append('\n');case 'r'->s.append('\r');case 't'->s.append('\t');case 'u'->{if(position+4>source.length())throw invalid();try{s.append((char)Integer.parseInt(source.substring(position,position+4),16));}catch(NumberFormatException e){throw invalid();}position+=4;}default->throw invalid();}}else s.append(c);}throw invalid();}
 private void space(){while(position<source.length()&&Character.isWhitespace(source.charAt(position)))position++;}
 private boolean take(char c){if(position<source.length()&&source.charAt(position)==c){position++;return true;}return false;}
 private void expect(char c){if(!take(c))throw invalid();}
 private static IllegalArgumentException invalid(){return new IllegalArgumentException("JSON không hợp lệ");}
}
