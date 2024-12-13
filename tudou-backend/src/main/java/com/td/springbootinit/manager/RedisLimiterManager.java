package com.td.springbootinit.manager;

import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.exception.BusinessException;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class RedisLimiterManager {
    @Resource
    private RedissonClient redissonClient;

    public void doRateLimit(String key){
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        rateLimiter.trySetRate(RateType.OVERALL,2,1, RateIntervalUnit.SECONDS);

        boolean tryAcquire = rateLimiter.tryAcquire(1);
        if(!tryAcquire){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST);
        }
    }
}
