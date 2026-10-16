package service;

import db.GiuChoTonDB;
import java.math.BigDecimal;

/** Hồi quy số lượng tối đa: kết quả phải bán được với 6 số lẻ và quy đổi ra số nguyên. */
public class StockAvailabilitySelfTest {
    private static int checks;

    private static void maximum(String available, String factor, String expected) {
        BigDecimal stock = new BigDecimal(available);
        BigDecimal conversion = new BigDecimal(factor);
        BigDecimal result = GiuChoTonDB.inUnit(stock, conversion);
        if (result.compareTo(new BigDecimal(expected)) != 0)
            throw new AssertionError(available + "/" + factor + ": " + result + " != " + expected);
        if (result.multiply(conversion).stripTrailingZeros().scale() > 0 ||
            result.multiply(conversion).compareTo(stock.max(BigDecimal.ZERO)) > 0 ||
            result.stripTrailingZeros().scale() > 6)
            throw new AssertionError("Số lượng tối đa không hợp lệ: " + result);
        checks++;
    }

    public static void main(String[] args) {
        maximum("12", "24", "0.5");
        maximum("1", "1", "1");
        maximum("2", "3", "0");
        maximum("1", "0.5", "2");
        maximum("0", "24", "0");
        maximum("-1", "24", "0");
        maximum("123", "100", "1.23");
        maximum("12.5", "24", "0.5");
        maximum("1000000000", "24", "41666666.625");
        System.out.println("Stock availability: " + checks + " checks passed; no database writes.");
    }
}
