package util;

import java.util.*;
import java.util.regex.*;

public final class YeuCauJson {

    private YeuCauJson() {}

    public static String text(String body, String key) {
        Matcher m = Pattern.compile(
            "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\""
        ).matcher(body == null ? "" : body);
        return m.find()
            ? m.group(1).replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
            : "";
    }

    public static long number(String body, String key, long fallback) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(
            body == null ? "" : body
        );
        return m.find() ? Long.parseLong(m.group(1)) : fallback;
    }

    public static boolean bool(String body, String key, boolean fallback) {
        Matcher m = Pattern.compile(
            "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)",
            Pattern.CASE_INSENSITIVE
        ).matcher(body == null ? "" : body);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : fallback;
    }

    public static List<String> strings(String body, String key) {
        Matcher m = Pattern.compile(
            "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]",
            Pattern.DOTALL
        ).matcher(body == null ? "" : body);
        if (!m.find()) return List.of();
        List<String> out = new ArrayList<>();
        Matcher values = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(m.group(1));
        while (values.find()) out.add(values.group(1));
        return out;
    }
}
