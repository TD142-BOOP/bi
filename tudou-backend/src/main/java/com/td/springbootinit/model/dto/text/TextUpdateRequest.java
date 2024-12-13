package com.td.springbootinit.model.dto.text;

import lombok.Data;

import java.io.Serializable;

@Data
public class TextUpdateRequest implements Serializable {
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
     * 生成的文本内容
     */
    private String genTextContent;

    /**
     * 执行信息
     */
    private String execMessage;

    private static final long serialVersionUID = 1L;
}
