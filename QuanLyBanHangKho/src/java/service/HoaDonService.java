package service;

import db.TruyVanDB;
import java.math.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import util.KetNoiDB;

/** S7-01: phát hành hóa đơn từ các phiếu giao thành công của cùng một đại lý. */
public final class HoaDonService {
    private static final int MAX_DELIVERIES = 100;

    public Map<String,Object> list() throws SQLException {
        try (Connection c=KetNoiDB.getConnection()) {
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("candidates",TruyVanDB.rows(c,
                "SELECT o.id AS order_id,o.code AS order_code,o.agent_id AS customer_id,k.code AS customer_code,k.name AS customer_name,"+
                "k.tax_code,dc.id AS confirmation_id,dc.delivered_at::text AS delivered_at,"+
                "COALESCE(SUM(round(ol.delivered_base_quantity/ol.conversion_factor*ol.line_total/ol.quantity,2)),0) AS delivered_total,"+
                "count(*) FILTER (WHERE ol.delivered_base_quantity>0) AS delivered_line_count " +
                "FROM orders o JOIN customers k ON k.id=o.agent_id JOIN delivery_confirmations dc ON dc.order_id=o.id AND dc.outcome='SUCCESS' " +
                "JOIN order_lines ol ON ol.order_id=o.id WHERE o.status IN ('DELIVERED','CLOSED') " +
                "AND NOT EXISTS(SELECT 1 FROM invoice_deliveries x WHERE x.order_id=o.id) " +
                "GROUP BY o.id,k.id,dc.id HAVING count(*) FILTER (WHERE ol.delivered_base_quantity>0)>0 " +
                "ORDER BY dc.delivered_at,o.id LIMIT 500"));
            out.put("invoices",TruyVanDB.rows(c,
                "SELECT i.id,i.invoice_number,i.period_from::text AS period_from,i.period_to::text AS period_to,i.total_amount,"+
                "i.issued_at::text AS issued_at,k.code AS customer_code,k.name AS customer_name,k.tax_code,u.full_name AS issued_by_name,"+
                "(SELECT count(*) FROM invoice_deliveries d WHERE d.invoice_id=i.id) AS delivery_count,"+
                "(SELECT count(*) FROM invoice_adjustments a WHERE a.invoice_id=i.id) AS adjustment_count,"+
                "COALESCE((SELECT sum(a.amount) FROM invoice_adjustments a WHERE a.invoice_id=i.id),0) AS adjustment_total " +
                "FROM invoices i JOIN customers k ON k.id=i.customer_id JOIN users u ON u.id=i.issued_by " +
                "ORDER BY i.issued_at DESC,i.id DESC LIMIT 200"));
            return out;
        }
    }

    public Map<String,Object> detail(long id) throws SQLException {
        try (Connection c=KetNoiDB.getConnection()) {
            Map<String,Object> invoice=TruyVanDB.one(c,
                "SELECT i.*,i.period_from::text AS period_from_text,i.period_to::text AS period_to_text,i.issued_at::text AS issued_at_text,"+
                "k.code AS customer_code,k.name AS customer_name,k.tax_code,k.region,k.phone,u.full_name AS issued_by_name " +
                "FROM invoices i JOIN customers k ON k.id=i.customer_id JOIN users u ON u.id=i.issued_by WHERE i.id=?",id);
            if(invoice==null)throw new NoSuchElementException("Không tìm thấy hóa đơn");
            invoice.remove("period_from");invoice.put("period_from",invoice.remove("period_from_text"));
            invoice.remove("period_to");invoice.put("period_to",invoice.remove("period_to_text"));
            invoice.remove("issued_at");invoice.put("issued_at",invoice.remove("issued_at_text"));
            invoice.put("deliveries",TruyVanDB.rows(c,
                "SELECT o.id AS order_id,o.code AS order_code,dc.delivered_at::text AS delivered_at,dc.recipient_name " +
                "FROM invoice_deliveries d JOIN orders o ON o.id=d.order_id JOIN delivery_confirmations dc ON dc.id=d.delivery_confirmation_id " +
                "WHERE d.invoice_id=? ORDER BY dc.delivered_at,o.id",id));
            invoice.put("lines",TruyVanDB.rows(c,
                "SELECT l.id,o.code AS order_code,p.sku,p.name,u.name AS unit_name,l.delivered_quantity,l.unit_price,l.subtotal,l.discount_amount,l.line_total " +
                "FROM invoice_lines l JOIN orders o ON o.id=l.order_id JOIN products p ON p.id=l.product_id JOIN product_units u ON u.id=l.unit_id " +
                "WHERE l.invoice_id=? ORDER BY o.code,p.sku,l.id",id));
            invoice.put("adjustments",TruyVanDB.rows(c,
                "SELECT a.id,a.adjustment_number,a.amount,a.reason,a.created_at::text AS created_at,u.full_name AS created_by_name " +
                "FROM invoice_adjustments a JOIN users u ON u.id=a.created_by WHERE a.invoice_id=? ORDER BY a.created_at,a.id",id));
            return invoice;
        }
    }

    public Map<String,Object> issue(long actor,Map<String,Object> input) throws SQLException {
        LocalDate from=date(input.get("periodFrom"),"Từ ngày"),to=date(input.get("periodTo"),"Đến ngày");
        if(to.isBefore(from))throw new IllegalArgumentException("Đến ngày không được trước từ ngày");
        String note=KhoService.text(input.get("note"),"Ghi chú",1000,false);
        Object raw=input.get("orderIds");
        if(!(raw instanceof List<?> values)||values.isEmpty()||values.size()>MAX_DELIVERIES)
            throw new IllegalArgumentException("Chọn từ 1 đến "+MAX_DELIVERIES+" phiếu giao");
        LinkedHashSet<Long> orderIds=new LinkedHashSet<>();
        for(Object value:values)if(!orderIds.add(KhoService.id(value)))throw new IllegalArgumentException("Phiếu giao bị chọn trùng");
        String marks=String.join(",",Collections.nCopies(orderIds.size(),"?"));
        try(Connection c=KetNoiDB.getConnection()){
            c.setAutoCommit(false);
            try{
                KhoService.actor(c,actor);
                List<Map<String,Object>> orders=TruyVanDB.rows(c,
                    "SELECT o.id,o.code,o.status,o.agent_id,dc.id AS confirmation_id,dc.delivered_at::date AS delivered_date " +
                    "FROM orders o JOIN delivery_confirmations dc ON dc.order_id=o.id AND dc.outcome='SUCCESS' " +
                    "WHERE o.id IN ("+marks+") ORDER BY o.id FOR UPDATE OF o",orderIds.toArray());
                if(orders.size()!=orderIds.size())throw new IllegalArgumentException("Có phiếu giao không tồn tại hoặc chưa giao thành công");
                Long customer=null;
                for(Map<String,Object> order:orders){
                    if(!Set.of("DELIVERED","CLOSED").contains(String.valueOf(order.get("status"))))throw new IllegalArgumentException("Đơn "+order.get("code")+" chưa ở trạng thái Đã giao");
                    if(order.get("agent_id")==null)throw new IllegalArgumentException("Đơn "+order.get("code")+" chưa gắn đại lý");
                    long current=((Number)order.get("agent_id")).longValue();
                    if(customer==null){customer=current;ThanhToanService.lockCustomer(c,customer);}else if(customer!=current)throw new IllegalArgumentException("Chỉ được gộp phiếu giao của cùng một đại lý");
                    LocalDate delivered=((java.sql.Date)order.get("delivered_date")).toLocalDate();
                    if(delivered.isBefore(from)||delivered.isAfter(to))throw new IllegalArgumentException("Phiếu "+order.get("code")+" nằm ngoài kỳ hóa đơn");
                    if(TruyVanDB.one(c,"SELECT 1 FROM invoice_deliveries WHERE order_id=?",order.get("id"))!=null)throw new IllegalArgumentException("Phiếu "+order.get("code")+" đã được xuất hóa đơn");
                }
                Map<String,Object> totals=TruyVanDB.one(c,
                    "SELECT SUM(round((ol.line_total+ol.discount_amount)/ol.quantity*(ol.delivered_base_quantity/ol.conversion_factor),2)) AS subtotal,"+
                    "SUM(round(ol.line_total/ol.quantity*(ol.delivered_base_quantity/ol.conversion_factor),2)) AS total " +
                    "FROM order_lines ol WHERE ol.order_id IN ("+marks+") AND ol.delivered_base_quantity>0",orderIds.toArray());
                if(totals==null||totals.get("total")==null)throw new IllegalArgumentException("Các phiếu đã chọn không có số lượng thực giao");
                BigDecimal subtotal=(BigDecimal)totals.get("subtotal"),total=(BigDecimal)totals.get("total"),discount=subtotal.subtract(total);
                long serial=((Number)TruyVanDB.one(c,"UPDATE invoice_number_counters SET last_number=last_number+1 WHERE document_type='INVOICE' RETURNING last_number").get("last_number")).longValue();
                String number=String.format("HD-%06d",serial);
                long invoiceId=TruyVanDB.id(TruyVanDB.one(c,
                    "INSERT INTO invoices(invoice_number,customer_id,period_from,period_to,subtotal,discount_total,total_amount,issued_by,note) VALUES(?,?,?,?,?,?,?,?,?) RETURNING id",
                    number,customer,from,to,subtotal,discount,total,actor,note));
                for(Map<String,Object> order:orders)TruyVanDB.update(c,"INSERT INTO invoice_deliveries(invoice_id,order_id,delivery_confirmation_id) VALUES(?,?,?)",invoiceId,order.get("id"),order.get("confirmation_id"));
                int inserted=TruyVanDB.update(c,
                    "INSERT INTO invoice_lines(invoice_id,order_id,order_line_id,product_id,unit_id,delivered_quantity,unit_price,subtotal,discount_amount,line_total) " +
                    "SELECT ?,ol.order_id,ol.id,ol.product_id,ol.unit_id,ol.delivered_base_quantity/ol.conversion_factor,round((ol.line_total+ol.discount_amount)/ol.quantity,2),"+
                    "round((ol.line_total+ol.discount_amount)/ol.quantity*(ol.delivered_base_quantity/ol.conversion_factor),2),"+
                    "round((ol.line_total+ol.discount_amount)/ol.quantity*(ol.delivered_base_quantity/ol.conversion_factor),2)-round(ol.line_total/ol.quantity*(ol.delivered_base_quantity/ol.conversion_factor),2),"+
                    "round(ol.line_total/ol.quantity*(ol.delivered_base_quantity/ol.conversion_factor),2) FROM order_lines ol " +
                    "WHERE ol.order_id IN ("+marks+") AND ol.delivered_base_quantity>0 ORDER BY ol.order_id,ol.id",prepend(invoiceId,orderIds.toArray()));
                if(inserted==0)throw new IllegalArgumentException("Không có dòng thực giao để phát hành hóa đơn");
                c.commit();return detail(invoiceId);
            }catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
    }

    public Map<String,Object> adjust(long invoiceId,long actor,Map<String,Object> input)throws SQLException{
        BigDecimal amount=money(input.get("amount"),"Số tiền điều chỉnh");
        if(amount.signum()==0)throw new IllegalArgumentException("Số tiền điều chỉnh phải khác 0");
        String reason=KhoService.text(input.get("reason"),"Lý do điều chỉnh",1000,true);
        try(Connection c=KetNoiDB.getConnection()){c.setAutoCommit(false);try{
            KhoService.actor(c,actor);
            var invoice=TruyVanDB.one(c,"SELECT customer_id FROM invoices WHERE id=?",invoiceId);
            if(invoice==null)throw new NoSuchElementException("Không tìm thấy hóa đơn");
            ThanhToanService.lockCustomer(c,((Number)invoice.get("customer_id")).longValue());
            var balance=TruyVanDB.one(c,"SELECT outstanding FROM receivable_invoice_balances WHERE id=?",invoiceId);
            if(((BigDecimal)balance.get("outstanding")).add(amount).signum()<0)throw new IllegalArgumentException("Không thể giảm hóa đơn dưới số tiền đã thu; hoàn tác khoản thu trước khi điều chỉnh");
            if(TruyVanDB.one(c,"SELECT id FROM invoices WHERE id=? FOR SHARE",invoiceId)==null)throw new NoSuchElementException("Không tìm thấy hóa đơn");
            long serial=((Number)TruyVanDB.one(c,"UPDATE invoice_number_counters SET last_number=last_number+1 WHERE document_type='ADJUSTMENT' RETURNING last_number").get("last_number")).longValue();
            String number=String.format("DC-%06d",serial);
            TruyVanDB.update(c,"INSERT INTO invoice_adjustments(adjustment_number,invoice_id,amount,reason,created_by) VALUES(?,?,?,?,?)",number,invoiceId,amount,reason,actor);
            c.commit();return detail(invoiceId);
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }

    private static Object[] prepend(Object first,Object[] rest){Object[] out=new Object[rest.length+1];out[0]=first;System.arraycopy(rest,0,out,1,rest.length);return out;}
    private static LocalDate date(Object value,String label){try{return LocalDate.parse(Objects.toString(value,""));}catch(RuntimeException e){throw new IllegalArgumentException(label+" không hợp lệ");}}
    private static BigDecimal money(Object value,String label){try{BigDecimal n=new BigDecimal(Objects.toString(value,""));if(n.scale()>2||n.precision()-n.scale()>16)throw new NumberFormatException();return n.setScale(2);}catch(RuntimeException e){throw new IllegalArgumentException(label+" không hợp lệ");}}
}
