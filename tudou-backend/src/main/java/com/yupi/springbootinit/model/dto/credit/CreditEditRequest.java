package com.yupi.springbootinit.model.dto.credit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 编辑请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Data
public class CreditEditRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 总积分
     */
    private Long creditTotal;


    private static final long serialVersionUID = 1L;
}
