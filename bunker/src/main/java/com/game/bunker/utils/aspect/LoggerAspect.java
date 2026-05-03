package com.game.bunker.utils.aspect;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggerAspect {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Around("execution(* com.game.bunker.service..*.*(..)) || execution(* com.game.bunker.controller..*.*(..))")
    Object logAround(ProceedingJoinPoint joinPoint) throws  Throwable{
        long start = System.currentTimeMillis();

        Object proceed = joinPoint.proceed();

        long executionTime = System.currentTimeMillis() - start;
        logger.info("{} выполнен за {} мс, результат: {}",
                joinPoint.getSignature(),
                executionTime,
                proceed);
        return proceed;
    }
}
