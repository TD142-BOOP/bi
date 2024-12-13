package com.td.springbootinit.service;

import com.td.springbootinit.model.entity.TextRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 86147
* @description 针对表【text_record(文本记录表)】的数据库操作Service
* @createDate 2024-05-30 20:11:55
*/
public interface TextRecordService extends IService<TextRecord> {

    String buildUserInput(TextRecord textRecord, String textType);
}
