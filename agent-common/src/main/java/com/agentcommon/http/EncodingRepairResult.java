package com.agentcommon.http;

/**
 * 编码修复结果封装
 */
public class EncodingRepairResult {

    private final String original;
    private final String repaired;
    private final boolean wasRepaired;

    public EncodingRepairResult(String original, String repaired, boolean wasRepaired) {
        this.original = original;
        this.repaired = repaired;
        this.wasRepaired = wasRepaired;
    }

    public String getOriginal() {
        return original;
    }

    public String getRepaired() {
        return repaired;
    }

    public boolean wasRepaired() {
        return wasRepaired;
    }

    public static EncodingRepairResult notRepaired(String content) {
        return new EncodingRepairResult(content, content, false);
    }

    public static EncodingRepairResult repaired(String original, String repaired) {
        return new EncodingRepairResult(original, repaired, true);
    }
}
