package com.td.springbootinit.model.dto.chart;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 创建请求
 *
 
 */
@Data
public class ChartAddRequest implements Serializable {

    private String goal;

    /**
     * 图表数据
     */
    private String chartdata;

    /**
     * 图表类型
     */
    private String chatType;

    /**
     * 图标名称
     */
    private String name;

    private static final long serialVersionUID = 1L;
}