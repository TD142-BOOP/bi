package com.td.springbootinit.service;

import com.td.springbootinit.model.dto.text.GenTextByAiRequest;
import com.td.springbootinit.model.entity.TextTask;
import com.baomidou.mybatisplus.extension.service.IService;
import com.td.springbootinit.model.entity.User;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
* @author 86147
* @description 针对表【text_task(文本任务表)】的数据库操作Service
* @createDate 2024-05-30 20:11:48
*/
public interface TextTaskService extends IService<TextTask> {



    @Transactional(rollbackFor = Exception.class)
    TextTask getTextTask(MultipartFile multipartFile, GenTextByAiRequest genTextByAiRequest, User loginUser);

    void handleTextTaskUpdateError(Long id, String message);
}
