package com.td.springbootinit.model.dto.alipay;

import lombok.Data;

import java.io.Serializable;


/**
 * 创建请求
 *
 
 */
@Data
public class AlipayAddRequest implements Serializable {

    /**
     * 交易名称
     */
    private String subject;

    /**
     * 交易金额
     */
    private Double totalAmount;

    private static final long serialVersionUID = 1L;

}