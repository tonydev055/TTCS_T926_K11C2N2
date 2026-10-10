package service;

import java.math.*;
import java.util.*;

/** Highest eligible quantity tier per policy, then best monetary saving; never stack.
 *  A GROUP policy covers its category and every descendant category.
 *  Trong một đơn, bậc xét theo tổng số lượng của SKU (mọi đơn vị tính) hoặc của cả nhóm hàng. */
public final class ChinhSachChietKhauService {
    private ChinhSachChietKhauService() {}
    public static BigDecimal decimal(Object value) {
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Giá trị số không hợp lệ"); }
    }
    public static long positiveId(Object value) {
        try {
            long id = decimal(value).longValueExact();
            if (id <= 0) throw new ArithmeticException();
            return id;
        } catch (ArithmeticException e) { throw new IllegalArgumentException("ID hoặc số lượng không hợp lệ"); }
    }
    public static Map<String,Object> validate(Map<String,Object> input) {
        String name = Objects.toString(input.get("name"), "").trim();
        String scope = Objects.toString(input.get("scope"), "");
        String dtype = Objects.toString(input.get("dtype"), "");
        if (name.isEmpty() || name.length()>200) throw new IllegalArgumentException("Tên chính sách phải có 1–200 ký tự");
        if (!Set.of("SKU","GROUP").contains(scope) || !Set.of("PERCENT","FIXED").contains(dtype))
            throw new IllegalArgumentException("Phạm vi hoặc loại chiết khấu không hợp lệ");
        long tid = positiveId(input.get("tid"));
        Object active = input.getOrDefault("active", true);
        if (!(active instanceof Boolean)) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        if (!(input.get("tiers") instanceof List<?> raw) || raw.isEmpty() || raw.size()>100)
            throw new IllegalArgumentException("Cần từ 1 đến 100 bậc chiết khấu");
        List<Map<String,Object>> tiers = new ArrayList<>();
        Set<Long> quantities = new HashSet<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?,?> t)) throw new IllegalArgumentException("Bậc không hợp lệ");
            long q = positiveId(t.get("q"));
            BigDecimal v = decimal(t.get("v"));
            if (q>Integer.MAX_VALUE || !quantities.add(q)) throw new IllegalArgumentException("Bậc số lượng bị trùng hoặc quá lớn");
            if (v.signum()<=0 || v.scale()>2 || v.precision()-v.scale()>16 ||
                (dtype.equals("PERCENT") && v.compareTo(new BigDecimal("100"))>0))
                throw new IllegalArgumentException("Chiết khấu phải dương, tối đa 2 số lẻ; phần trăm không vượt 100%");
            tiers.add(Map.of("q",q,"v",v));
        }
        tiers.sort(Comparator.comparingLong(t -> ((Number)t.get("q")).longValue()));
        return new LinkedHashMap<>(Map.of("name",name,"scope",scope,"tid",String.valueOf(tid),"dtype",dtype,"active",active,"tiers",tiers));
    }
    /** {@code categories}: nhóm của sản phẩm và toàn bộ nhóm cha của nó. */
    public static Map<String,Object> quote(List<Map<String,Object>> policies, long product, Set<Long> categories,
                                           int qty, BigDecimal price) {
        return quote(policies, product, categories, qty, price, qty, Map.of());
    }

    /**
     * Như trên, nhưng bậc chiết khấu xét theo tổng số lượng của cả đơn: {@code skuQty} là tổng số lượng
     * (đơn vị cơ sở) của SKU này, {@code groupQty} là tổng số lượng theo từng nhóm hàng (tính cả nhóm con).
     * Nhóm không có trong {@code groupQty} dùng số lượng của dòng. Chiết khấu vẫn nhân với số lượng của dòng.
     */
    public static Map<String,Object> quote(List<Map<String,Object>> policies, long product, Set<Long> categories,
                                           int qty, BigDecimal price, long skuQty, Map<Long,Long> groupQty) {
        if (qty<1 || price.signum()<0 || price.scale()>2 || price.precision()-price.scale()>16)
            throw new IllegalArgumentException("Số lượng hoặc đơn giá không hợp lệ");
        List<Map<String,Object>> results = new ArrayList<>();
        BigDecimal best = BigDecimal.ZERO;
        Long winner = null;
        for (Map<String,Object> p : policies) {
            if (!Boolean.TRUE.equals(p.get("active"))) continue;
            long target = positiveId(p.get("tid"));
            if (p.get("scope").equals("SKU") ? target != product : !categories.contains(target)) continue;
            long reached = p.get("scope").equals("SKU") ? Math.max(qty, skuQty) : Math.max(qty, groupQty.getOrDefault(target, (long)qty));
            Map<?,?> tier = null;
            for (Object item : (List<?>)p.get("tiers")) {
                Map<?,?> t = (Map<?,?>)item;
                long q = positiveId(t.get("q"));
                if (q<=reached && (tier==null || q>positiveId(tier.get("q")))) tier=t;
            }
            if (tier==null) continue;
            BigDecimal value = decimal(tier.get("v"));
            BigDecimal saving = p.get("dtype").equals("PERCENT")
                ? price.multiply(value).divide(new BigDecimal("100")) : value;
            saving = saving.min(price).setScale(2,RoundingMode.HALF_UP);
            long id = ((Number)p.get("id")).longValue();
            if (winner==null || saving.compareTo(best)>0 || (saving.compareTo(best)==0 && id<winner)) {
                best=saving; winner=id;
            }
            results.add(new LinkedHashMap<>(Map.of("p",p,"tier",tier,"dpu",saving,"total",saving.multiply(BigDecimal.valueOf(qty)))));
        }
        for (Map<String,Object> row : results)
            row.put("isBest", Objects.equals(((Map<?,?>)row.get("p")).get("id"),winner));
        BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
        BigDecimal discount = best.multiply(BigDecimal.valueOf(qty));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("policyId",winner); out.put("results",results); out.put("subtotal",subtotal);
        out.put("discount",discount); out.put("payment",subtotal.subtract(discount));
        return out;
    }
}
