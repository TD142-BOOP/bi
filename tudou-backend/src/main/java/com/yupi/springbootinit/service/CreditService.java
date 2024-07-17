package com.yupi.springbootinit.service;

import com.yupi.springbootinit.model.entity.Credit;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 86147
* @description 针对表【credit(积分表)】的数据库操作Service
* @createDate 2024-06-01 13:05:27
*/
public interface CreditService extends IService<Credit> {
    Long getCreditTotal(Long userId);

    Boolean getSign(Long userId);

    boolean updateCredits(Long id, long l);
}
