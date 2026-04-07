package com.agentcommon.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class CharsetFixUtils {

    private static final double MESSY_RATIO_THRESHOLD = 0.30d;

    private CharsetFixUtils() {
    }

    public static boolean isMessyCode(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }

        int total = 0;
        int weird = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            total++;
            if (isWeirdChar(c)) {
                weird++;
            }
        }

        if (total == 0) {
            return false;
        }
        return ((double) weird / (double) total) > MESSY_RATIO_THRESHOLD;
    }

    public static String fixMessyCode(String s) {
        if (s == null || s.isBlank()) {
            return s;
        }
        if (!isMessyCode(s)) {
            return s;
        }

        String once = bestFromLatin1Bytes(s);
        if (!isMessyCode(once)) {
            return once;
        }

        String twice = bestFromLatin1Bytes(once);
        if (!isMessyCode(twice)) {
            return twice;
        }

        return s;
    }

    private static String bestFromLatin1Bytes(String s) {
        byte[] raw = s.getBytes(StandardCharsets.ISO_8859_1);
        String utf8 = new String(raw, StandardCharsets.UTF_8);
        String gbk = new String(raw, Charset.forName("GBK"));

        String best = s;
        int bestScore = score(s);
        int utf8Score = score(utf8);
        if (utf8Score > bestScore) {
            best = utf8;
            bestScore = utf8Score;
        }
        int gbkScore = score(gbk);
        if (gbkScore > bestScore) {
            best = gbk;
        }
        return best;
    }

    private static boolean isWeirdChar(char c) {
        if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') {
            return true;
        }
        if (c == '\uFFFD') {
            return true;
        }
        if (!Character.isDefined(c)) {
            return true;
        }
        if (c == '\u25A1') {
            return true;
        }
        if (c >= 0x80 && c <= 0x9F) {
            return true;
        }
        return c >= 0xE0 && c <= 0xEF;
    }

    private static int score(String s) {
        if (s == null || s.isBlank()) {
            return Integer.MIN_VALUE / 2;
        }
        int cjk = 0;
        int ascii = 0;
        int weird = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                cjk++;
            }
            if (c >= 32 && c <= 126) {
                ascii++;
            }
            if (isWeirdChar(c)) {
                weird++;
            }
        }
        return cjk * 4 + ascii - weird * 10;
    }
}
