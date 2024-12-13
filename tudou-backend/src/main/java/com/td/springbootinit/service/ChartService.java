package com.td.springbootinit.service;

import com.td.springbootinit.model.dto.chart.GenChartByAiRequest;
import com.td.springbootinit.model.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.td.springbootinit.model.entity.User;
import com.td.springbootinit.model.vo.BiResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
* @author 86147
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2024-04-26 11:44:54
*/
public interface ChartService extends IService<Chart> {

    @Transactional(rollbackFor = Exception.class)
    Chart getChartTask(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser);

    BiResponse saveChartAiResult(String result, long chartId);

    void handleChartUpdateError(Long chartId, String execMessage);

    Chart getChartById(Long id);
}
