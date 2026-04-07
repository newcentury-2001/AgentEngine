package com.agentcommon.http;

import com.agentcommon.util.CharsetFixUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 自动修复 HTTP 响应编码的 AOP 切面
 * <p>
 * 对 HttpHelper 的所有返回 String 的方法自动应用编码修复
 * 如果是 JSON，递归检查每个文本字段，只要任意字段乱码就修复整个 JSON
 */
@Aspect
public class EncodingRepairAspect {

    @Pointcut("execution(* com.agentcommon.http.HttpHelper.*(..))")
    public void httpHelperMethods() {
    }

    @Around("httpHelperMethods()")
    public Object repairEncoding(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        if (result instanceof String) {
            String content = (String) result;
            if (shouldRepair(content)) {
                String repaired = CharsetFixUtils.fixMessyCode(content);
                return EncodingRepairResult.repaired(content, repaired);
            } else {
                return EncodingRepairResult.notRepaired(content);
            }
        }

        return result;
    }

    /**
     * 判断是否需要修复编码
     * <p>
     * 如果是 JSON，递归检查每个字段，只要任意字段乱码就返回 true
     * 如果不是 JSON，直接检查整体
     */
    private boolean shouldRepair(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }

        // 尝试解析为 JSON
        if (isJson(content)) {
            return hasMessyInJson(content);
        }

        // 不是 JSON，直接检查整体
        return CharsetFixUtils.isMessyCode(content);
    }

    /**
     * 简单判断是否为 JSON
     */
    private boolean isJson(String content) {
        String trimmed = content.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /**
     * 递归检查 JSON 中是否有乱码
     */
    private boolean hasMessyInJson(String content) {
        // 简单实现：提取所有字符串字面量进行检查
        // 注意：这里用简化的方法，实际项目可以用 Jackson 完整解析
        return extractStringLiterals(content).stream()
                .anyMatch(CharsetFixUtils::isMessyCode);
    }

    /**
     * 从 JSON 中提取字符串字面量（简化版）
     */
    private java.util.List<String> extractStringLiterals(String json) {
        java.util.List<String> literals = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                if (inString) {
                    current.append(c);
                }
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                if (inString) {
                    current.append(c);
                }
                continue;
            }

            if (c == '"') {
                if (inString) {
                    // 字符串结束
                    literals.add(current.toString());
                    current.setLength(0);
                }
                inString = !inString;
                continue;
            }

            if (inString) {
                current.append(c);
            }
        }

        return literals;
    }
}
