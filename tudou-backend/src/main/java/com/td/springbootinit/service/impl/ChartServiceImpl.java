package com.td.springbootinit.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.constant.ChartConstant;
import com.td.springbootinit.constant.CreditConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.exception.ThrowUtils;
import com.td.springbootinit.model.dto.chart.GenChartByAiRequest;
import com.td.springbootinit.model.entity.Chart;
import com.td.springbootinit.model.entity.User;
import com.td.springbootinit.model.vo.BiResponse;
import com.td.springbootinit.service.ChartService;
import com.td.springbootinit.mapper.ChartMapper;
import com.td.springbootinit.service.CreditService;
import com.td.springbootinit.utils.ExcelUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author MA_dou
 * @description 针对表【chart(图表信息表)】的数据库操作Service实现
 * @createDate 2023-05-26 23:18:07
 */
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
        implements ChartService{
    @Resource
    private CreditService creditService;


    @Transactional(rollbackFor = Exception.class)
    @Override
    public Chart getChartTask(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser) {
        String chartType = genChartByAiRequest.getChartType();
        String goal = genChartByAiRequest.getGoal();
        String name = genChartByAiRequest.getName();
        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR,"目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name)&&name.length()>=100,ErrorCode.PARAMS_ERROR,"名称不规范");
        //校验文件
        long size = multipartFile.getSize();
        String originalFilename = multipartFile.getOriginalFilename();
        final long ONE_MB = 1024*1024;
        ThrowUtils.throwIf(size>ONE_MB,ErrorCode.PARAMS_ERROR,"文件超过1MB");
        ThrowUtils.throwIf(size==0,ErrorCode.PARAMS_ERROR,"文件为空");
        //校验文件后缀
        String suffix = FileUtil.getSuffix(originalFilename);
        final List<String> validFileSuffix = Arrays.asList("xlsx");
        ThrowUtils.throwIf(!validFileSuffix.contains(suffix),ErrorCode.PARAMS_ERROR,"文件后缀名非法");

        // 压缩后的数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        //消耗积分
        Boolean creditResult = creditService.updateCredits(loginUser.getId(), CreditConstant.CREDIT_CHART_SUCCESS);
        ThrowUtils.throwIf(!creditResult,ErrorCode.OPERATION_ERROR,"你的积分不足");
        //保存数据库 wait
        Chart chart = new Chart();
        chart.setUserId(loginUser.getId());
        chart.setChartdata(csvData);
        chart.setChatType(chartType);
        chart.setStatus(ChartConstant.WAIT);
        chart.setName(name);
        chart.setGoal(goal);
        boolean saveResult = this.save(chart);
        ThrowUtils.throwIf(!saveResult,ErrorCode.SYSTEM_ERROR,"图表保存失败");
        return chart;
    }

    @Override
    public BiResponse saveChartAiResult(String result, long chartId) {
        String[] splits = result.split("【【【");
        if (splits.length <= 2){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 生成错误");
        }
        String genChart= splits[1].trim();
        String genResult = splits[2].trim();
        //将非js格式转化为js格式
        try {
            HashMap<String,Object> genChartJson = JSONUtil.toBean(genChart, HashMap.class);
            genChart = JSONUtil.toJsonStr(genChartJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI生成图片错误");
        }
        //保存数据库
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(ChartConstant.SUCCEED);
        updateChartResult.setGenChat(genChart);
        String[] split = genResult.split("】】】");
        genResult= split[0];
        updateChartResult.setGenResult(genResult);
        try {
            boolean updateById = this.updateById(updateChartResult);
            ThrowUtils.throwIf(!updateById,ErrorCode.OPERATION_ERROR,"保存失败");
        } catch (Exception e) {

        }
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chartId);
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        return biResponse;
    }

    @Override
    public void handleChartUpdateError(Long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setStatus(ChartConstant.FAILED);
        updateChartResult.setId(chartId);
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = this.updateById(updateChartResult);
        if (!updateResult){
            log.error("更新图片失败状态失败"+chartId+","+execMessage);
        }
    }

    @Override
    public Chart getChartById(Long id) {
        ThrowUtils.throwIf(id==null||id<1,ErrorCode.PARAMS_ERROR);
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id",id);
        return this.baseMapper.selectOne(queryWrapper);
    }
}