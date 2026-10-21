package service;
import db.TruyVanDB;
import util.KetNoiDB;
import security.PhanQuyen;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.*;
import java.util.*;

public final class BaoCaoDoanhSoService {
 public Map<String,Object> report(Collection<String> roles,Map<String,String> f)throws SQLException{
  if(!PhanQuyen.allows(roles,"reports.sales"))throw new SecurityException("Bạn chưa được cấp quyền xem báo cáo doanh số");
  boolean margin=roles.contains("SALES_MANAGER");String group=f.getOrDefault("groupBy","employee"),compare=f.getOrDefault("compare","previous_period");
  if(!Set.of("employee","region","dealer","category","sku").contains(group))throw new IllegalArgumentException("Chiều phân tích không hợp lệ");
  if(!Set.of("none","previous_period","previous_month","previous_year").contains(compare))throw new IllegalArgumentException("Kỳ so sánh không hợp lệ");
  LocalDate from,to;try{from=LocalDate.parse(f.getOrDefault("from",LocalDate.now().withDayOfMonth(1).toString()));to=LocalDate.parse(f.getOrDefault("to",LocalDate.now().toString()));}catch(RuntimeException e){throw new IllegalArgumentException("Ngày báo cáo không hợp lệ");}
  if(from.isAfter(to)||to.isAfter(LocalDate.now())||ChronoUnit.DAYS.between(from,to)>366)throw new IllegalArgumentException("Chọn kỳ tối đa 367 ngày, không sau hôm nay");
  LocalDate prevFrom=null,prevTo=null;
  if(compare.equals("previous_period")){prevTo=from.minusDays(1);prevFrom=prevTo.minusDays(ChronoUnit.DAYS.between(from,to));}
  else if(compare.equals("previous_month")){prevFrom=from.minusMonths(1);prevTo=to.minusMonths(1);}
  else if(compare.equals("previous_year")){prevFrom=from.minusYears(1);prevTo=to.minusYears(1);}
  String key,label;
  switch(group){
   case "region" -> {key="COALESCE(NULLIF(k.region,''),'Chưa phân vùng')";label=key;}
   case "dealer" -> {key="k.id::text";label="k.code||' · '||k.name";}
   case "category" -> {key="c.id::text";label="c.code||' · '||c.name";}
   case "sku" -> {key="p.id::text";label="p.sku||' · '||p.name";}
   default -> {key="COALESCE(u.id,0)::text";label="COALESCE(u.full_name,'Chưa phân công')";}
  }
  String current="o.created_at>=b.a AND o.created_at<b.z",previous="o.created_at>=b.pa AND o.created_at<b.pz";
  String sql="WITH bounds AS(SELECT ?::timestamp a,?::timestamp z,?::timestamp pa,?::timestamp pz) SELECT "+key+" group_id,"+label+" group_key,"+
   "COALESCE(sum(ol.line_total) FILTER(WHERE "+current+"),0) revenue,COALESCE(sum(ol.base_quantity) FILTER(WHERE "+current+"),0) quantity,"+
   (margin?"COALESCE(sum(ol.base_quantity*p.cost_price) FILTER(WHERE "+current+"),0)":"0::numeric")+" cogs,"+
   "COALESCE(sum(ol.line_total) FILTER(WHERE "+previous+"),0) prev_revenue,COALESCE(sum(ol.base_quantity) FILTER(WHERE "+previous+"),0) prev_quantity "+
   "FROM orders o JOIN order_lines ol ON ol.order_id=o.id JOIN products p ON p.id=ol.product_id JOIN customers k ON k.id=o.agent_id JOIN categories c ON c.id=p.category_id LEFT JOIN users u ON u.id=k.sales_rep_id CROSS JOIN bounds b "+
   "WHERE o.status IN('APPROVED','PICKING','SHIPPED','DELIVERED','CLOSED') AND (("+current+") OR ("+previous+")) GROUP BY "+key+","+label+" ORDER BY revenue DESC,group_id LIMIT 50001";
  try(Connection c=KetNoiDB.getConnection()){
   var rows=TruyVanDB.rows(c,sql,Timestamp.valueOf(from.atStartOfDay()),Timestamp.valueOf(to.plusDays(1).atStartOfDay()),prevFrom==null?null:Timestamp.valueOf(prevFrom.atStartOfDay()),prevTo==null?null:Timestamp.valueOf(prevTo.plusDays(1).atStartOfDay()));
   if(rows.size()>50000)throw new IllegalArgumentException("Báo cáo vượt 50.000 nhóm; hãy thu hẹp kỳ báo cáo");
   var summary=new LinkedHashMap<String,Object>();for(String field:List.of("revenue","quantity","cogs","prev_revenue","prev_quantity"))summary.put(field,BigDecimal.ZERO);
   for(var row:rows){
    for(String field:List.of("revenue","quantity","cogs","prev_revenue","prev_quantity"))summary.put(field,((BigDecimal)summary.get(field)).add((BigDecimal)row.get(field)));
    row.put("gross_margin_pct",margin?ratio(((BigDecimal)row.get("revenue")).subtract((BigDecimal)row.get("cogs")),(BigDecimal)row.get("revenue")):null);
    row.put("revenue_growth_pct",growth(row,"revenue","prev_revenue"));row.put("quantity_growth_pct",growth(row,"quantity","prev_quantity"));
    if(!margin)row.put("cogs",null);
   }
   summary.put("gross_margin_pct",margin?ratio(((BigDecimal)summary.get("revenue")).subtract((BigDecimal)summary.get("cogs")),(BigDecimal)summary.get("revenue")):null);
   summary.put("revenue_growth_pct",growth(summary,"revenue","prev_revenue"));summary.put("quantity_growth_pct",growth(summary,"quantity","prev_quantity"));if(!margin)summary.put("cogs",null);
   return Map.of("items",rows,"summary",summary,"canViewMargin",margin,"groupBy",group,"compare",compare,"from",from.toString(),"to",to.toString(),"prevFrom",prevFrom==null?"":prevFrom.toString(),"prevTo",prevTo==null?"":prevTo.toString());
  }
 }
 private static BigDecimal ratio(BigDecimal amount,BigDecimal base){return base.signum()==0?null:amount.multiply(BigDecimal.valueOf(100)).divide(base,2,RoundingMode.HALF_UP);}
 private static BigDecimal growth(Map<String,Object> row,String field,String previous){BigDecimal before=(BigDecimal)row.get(previous);return ratio(((BigDecimal)row.get(field)).subtract(before),before);}
}
