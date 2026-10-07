package com.frodo.bigbong.micro.framework.interceptor;

import com.frodo.bigbong.micro.framework.common.RpcContext;
import com.frodo.bigbong.micro.framework.common.RpcPageResponse;
import com.frodo.bigbong.micro.framework.common.RpcResponse;
import com.frodo.bigbong.micro.framework.exception.ArgumentException;
import com.frodo.bigbong.micro.framework.exception.BizException;
import com.frodo.bigbong.micro.framework.util.GsonUtils;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.util.ClassUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static com.frodo.bigbong.micro.framework.common.RpcResponse.ERROR_ARGUMENTS;

/**
 * @author frodoking on 2019/10/18.
 */
@Slf4j
public class MicroServiceAroundAdvice {

    public static Object around(ProceedingJoinPoint joinPoint) {
        long startTime = System.currentTimeMillis();
        String requestId = RpcContext.getRequestId();
        if (StringUtils.isBlank(requestId)) {
            requestId = "reqIdNA";
        }
        MDC.put("reqId", requestId);
        String remoteIP = RpcContext.getRemoteAddress();
        if (StringUtils.isBlank(remoteIP)) {
            remoteIP = "remIpNA";
        }

        Object target = joinPoint.getTarget();
        Signature signature = joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        Object object = null;
        try {
            object = joinPoint.proceed();
            return object;
        } catch (Throwable throwable) {
            object = RpcResponse.error(Throwables.getStackTraceAsString(throwable));
            if (throwable instanceof ArgumentException) {
                ArgumentException argumentException = (ArgumentException) throwable;
                log.warn("服务参数异常", argumentException);
                Class returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
                if (returnType == RpcResponse.class) {
                    object = RpcResponse.error(ERROR_ARGUMENTS, argumentException.getMessage());
                } else if (returnType == RpcPageResponse.class) {
                    object = RpcPageResponse.error(ERROR_ARGUMENTS, argumentException.getMessage());
                }
            } else if (throwable instanceof BizException) {
                BizException bizException = (BizException) throwable;
                log.warn("服务业务异常 {} : {}", bizException.getErrorCode(), bizException.getMessage());
                Class returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
                if (returnType == RpcResponse.class) {
                    object = RpcResponse.error(bizException.getErrorCode(), bizException.getMessage());
                } else if (returnType == RpcPageResponse.class) {
                    object = RpcPageResponse.error(bizException.getErrorCode(), bizException.getMessage());
                }
            } else {
                Class returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
                log.error("服务异常 ", throwable);
                if (returnType == RpcResponse.class) {
                    object = RpcResponse.error(throwable.getMessage());
                } else if (returnType == RpcPageResponse.class) {
                    object = RpcPageResponse.error(throwable.getMessage());
                }
            }

            return object;
        } finally {
            long takeTime = System.currentTimeMillis() - startTime;
            log.info("[ requestId {} ], [ remoteIp {} ], [ {}.{}, args {}, output {} ], [ take time {} ms ]",
                    requestId, remoteIP, target.getClass().getName(), signature.getName(), GsonUtils.toJson(args),
                    GsonUtils.toJson(object), takeTime);
            MDC.clear();
        }
    }

    public static Object aroundLocalService(ProceedingJoinPoint joinPoint, Boolean needPrintArgs) {
        long startTime = System.currentTimeMillis();
        String requestId = RpcContext.getRequestId();
        if (StringUtils.isBlank(requestId)) {
            requestId = "reqIdNA";
        }
        MDC.put("reqId", requestId);
        Object target = joinPoint.getTarget();
        Signature signature = joinPoint.getSignature();
        Object[] args = joinPoint.getArgs();
        Object object = null;
        try {
            object = joinPoint.proceed();
            return object;
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            } else {
                throw new RuntimeException(throwable);
            }
        } finally {
            long takeTime = System.currentTimeMillis() - startTime;
            if (needPrintArgs) {
                log.info("[ requestId {} ], [ {}.{}, args {}, output {} ], [ take time {} ms ]",
                        requestId, target.getClass().getName(), signature.getName(), GsonUtils.toJson(args),
                        GsonUtils.toJson(object), takeTime);
            } else {
                log.info("[ requestId {} ], [ {}.{} ], [ take time {} ms ]",
                        requestId, target.getClass().getName(), signature.getName(), takeTime);
            }
        }
    }

    public static <T extends Annotation> T parseAopAnnotation(ProceedingJoinPoint joinPoint, Class<T> annotationClass) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> userClass = ClassUtils.getUserClass(joinPoint.getTarget());
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, userClass);
        return specificMethod.getAnnotation(annotationClass);
    }
}
