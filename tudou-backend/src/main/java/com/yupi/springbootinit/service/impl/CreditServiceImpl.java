package com.yupi.springbootinit.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.constant.CreditConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.mapper.CreditMapper;
import com.yupi.springbootinit.model.entity.Credit;
import com.yupi.springbootinit.service.CreditService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
* @author 86147
* @description 针对表【credit(积分表)】的数据库操作Service实现
* @createDate 2024-06-01 13:05:27
*/
@Service
public class CreditServiceImpl extends ServiceImpl<CreditMapper, Credit>
    implements CreditService {

    @Override
    public Long getCreditTotal(Long userId) {
        if(userId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<Credit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId",userId);
        Credit credit = this.getOne(queryWrapper);
        ThrowUtils.throwIf(credit==null,ErrorCode.NOT_FOUND_ERROR);
        return credit.getCreditTotal();
    }

    @Override
    public Boolean getSign(Long userId) {
        ThrowUtils.throwIf(userId==null||userId<1,ErrorCode.NOT_LOGIN_ERROR);
        synchronized (userId.toString().intern()){
            QueryWrapper<Credit> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId",userId);
            Credit credit = this.getOne(queryWrapper);
            ThrowUtils.throwIf(credit==null,ErrorCode.NOT_FOUND_ERROR);
            if(DateUtil.isSameDay(credit.getUpdateTime(),new DateTime())){
                return false;
            }
            Long creditTotal = credit.getCreditTotal()+ CreditConstant.CREDIT_DAILY;
            credit.setCreditTotal(creditTotal);
            credit.setUpdateTime(null);
            return this.updateById(credit);
        }

    }

    @Override
    public boolean updateCredits(Long userId, long credits) {
        if(userId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        QueryWrapper<Credit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId",userId);
        Credit credit = this.getOne(queryWrapper);
        ThrowUtils.throwIf(credit==null,ErrorCode.NOT_FOUND_ERROR);
        Long creditTotal = credit.getCreditTotal();
        //积分不足时
        if (creditTotal+credits<0) return false;
        creditTotal =creditTotal + credits;
        credit.setCreditTotal(creditTotal);
        //保持更新时间
        credit.setUpdateTime(null);
        return this.updateById(credit);
    }
}




