package com.td.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.td.springbootinit.model.entity.TextRecord;
import com.td.springbootinit.service.TextRecordService;
import com.td.springbootinit.mapper.TextRecordMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
* @author 86147
* @description 针对表【text_record(文本记录表)】的数据库操作Service实现
* @createDate 2024-05-30 20:11:55
*/
@Service
public class TextRecordServiceImpl extends ServiceImpl<TextRecordMapper, TextRecord>
    implements TextRecordService{


    @Override
    public String buildUserInput(TextRecord textRecord, String textType) {
        String textContent = textRecord.getTextContent();

        StringBuilder userInput = new StringBuilder();
        String gold = "请使用"+textType+"语法对下面文章格式化";
        userInput.append(gold).append("\n");

        if(StringUtils.isNotBlank(textContent)){
            textContent=textContent.trim();
            userInput.append(textContent);
        }
        return userInput.toString();
    }

}




