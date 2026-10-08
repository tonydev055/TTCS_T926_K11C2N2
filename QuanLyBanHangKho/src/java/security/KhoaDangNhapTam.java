package security;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Khoá tạm cho tên đăng nhập không tồn tại, hành xử giống tài khoản thật
 * (5 lần sai liên tiếp → khoá 15 phút) để phản hồi không tiết lộ tài khoản có tồn tại hay không.
 * Chỉ giữ trong bộ nhớ; khởi động lại máy chủ thì bộ đếm về 0.
 */
public final class KhoaDangNhapTam {

    public static final int MAX_FAILURES = 5;
    public static final long LOCK_MILLIS = 15 * 60 * 1000L;
    private static final int MAX_ENTRIES = 10_000;
    private static final ConcurrentHashMap<String, long[]> STATE = new ConcurrentHashMap<>();

    private KhoaDangNhapTam() {}

    /** Số mili giây còn bị khoá, 0 nếu không bị khoá. */
    public static long remainingMillis(String identity) {
        long[] entry = STATE.get(key(identity));
        if (entry == null) return 0;
        synchronized (entry) {
            return Math.max(0, entry[1] - System.currentTimeMillis());
        }
    }

    public static void failed(String identity) {
        if (STATE.size() >= MAX_ENTRIES) prune();
        long[] entry = STATE.computeIfAbsent(key(identity), k -> new long[2]);
        synchronized (entry) {
            long now = System.currentTimeMillis();
            if (entry[1] != 0 && entry[1] <= now) {
                entry[0] = 0;
                entry[1] = 0;
            }
            entry[0]++;
            if (entry[0] >= MAX_FAILURES) entry[1] = now + LOCK_MILLIS;
        }
    }

    static void reset() {
        STATE.clear();
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        STATE.entrySet().removeIf(e -> e.getValue()[1] != 0 && e.getValue()[1] <= now);
        if (STATE.size() >= MAX_ENTRIES) STATE.clear();
    }

    private static String key(String identity) {
        return identity == null ? "" : identity.trim().toLowerCase(Locale.ROOT);
    }
}
