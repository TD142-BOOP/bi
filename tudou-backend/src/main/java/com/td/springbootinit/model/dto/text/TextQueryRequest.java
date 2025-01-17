package com.td.springbootinit.model.dto.text;
import com.td.springbootinit.common.PageRequest;
import lombok.Data;

import java.io.Serializable;

@Data
public class TextQueryRequest extends PageRequest implements Serializable {
    /**
     * 任务id
     */
    private Long id;

    /**
     * 笔记名称
     */
    private String name;

    /**
     * 文本类型
     */
    private String textType;


    /**
     * 创建用户Id
     */
    private Long userId;

    /**
     * wait,running,succeed,failed
     */
    private String status;

    private static final long serialVersionUID = 1L;
}
