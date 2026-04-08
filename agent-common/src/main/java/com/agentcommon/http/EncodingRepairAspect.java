package com.agentcommon.http;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class EncodingRepairAspect {

    private final EncodingRepairEngine repairEngine;

    public EncodingRepairAspect(EncodingRepairEngine repairEngine) {
        this.repairEngine = repairEngine;
    }

    @Around("@annotation(com.agentcommon.http.RepairEncoding)")
    public Object repairEncoding(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        if (result instanceof EncodingRepairResult repairResult) {
            String content = repairResult.getRepaired();
            String repaired = repairEngine.repairIfNeeded(content);
            if (!repaired.equals(content)) {
                return EncodingRepairResult.repaired(repairResult.getOriginal(), repaired);
            }
            return repairResult;
        }

        if (result instanceof String s) {
            return repairEngine.repairIfNeeded(s);
        }

        return result;
    }
}
