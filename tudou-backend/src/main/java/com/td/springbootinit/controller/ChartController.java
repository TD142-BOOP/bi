package com.td.springbootinit.controller;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.td.springbootinit.annotation.AuthCheck;
import com.td.springbootinit.bizmq.common.MqMessageProducer;
import com.td.springbootinit.common.*;
import com.td.springbootinit.constant.ChartConstant;
import com.td.springbootinit.constant.CommonConstant;
import com.td.springbootinit.constant.MqConstant;
import com.td.springbootinit.constant.UserConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.exception.ThrowUtils;
import com.td.springbootinit.manager.AiManager;
import com.td.springbootinit.manager.RedisLimiterManager;
import com.td.springbootinit.model.dto.chart.*;
import com.td.springbootinit.model.entity.Chart;
import com.td.springbootinit.model.entity.User;
import com.td.springbootinit.model.vo.AiResponse;
import com.td.springbootinit.model.vo.BiResponse;
import com.td.springbootinit.model.vo.ChartVO;
import com.td.springbootinit.service.ChartService;
import com.td.springbootinit.service.UserService;
import com.td.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 帖子接口
 *
 
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private RedisLimiterManager redisLimiterManager;
    @Resource
    private UserService userService;
    @Resource
    private AiCommon aiCommon;

    @Resource
    private AiManager aiManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;
    @Resource
    private MqMessageProducer mqMessageProducer;
    private final String AI_ANSWER_LOCK="AI_CHART_LOCK";
    @Resource
    private RedissonClient redissonClient;


    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    @GetMapping("/get/vo")
    public BaseResponse<ChartVO> getChartVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        ChartVO ChartVO = new ChartVO();
        BeanUtils.copyProperties(chart,ChartVO);
        return ResultUtils.success(ChartVO);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size), getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 智能分析（同步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws InterruptedException {
        BiResponse biResponse = aiCommon.getAiCommon(multipartFile, genChartByAiRequest, request);
        return ResultUtils.success(biResponse);
    }
    private static final String GENERATE_QUESTION_SYSTEM_MESSAGE = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
            "```\n" +
            "分析需求：\n" +
            "【【【数据分析的需求或者目标】】】\n" +
            "原始数据：\n" +
            "csv格式的原始数据，用,作为分隔符\n" +
            "```\n" +
            "\n" +
            "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
            "1. 前端 Echarts V5 的 option 配置对象js代码，合理地将数据进行可视化，不要生成任何多余的内容，比如注释\n" +
            "2. 明确的数据分析结论、越详细越好，不要生成多余的注释\n" +
            "3. 检查题目是否包含序号，若包含序号则去除序号\n" +
            "【【【\n"+
            "{\"title\": { \"text\": \"网站用户增长情况\", subtext: \"\"},\"tooltip\": {\"trigger\": \"axis\", \"axisPointer\": {\"type\": \"shadow\"}},\"legend\": {\"data\": [\"用户数\"]},\"xAxis\": {\"data\": [\"1号\", \"2号\", \"3号\"]},\"yAxis\": {},\"series\": [{\"name\": \"用户数\", \"type\": \"bar\", \"data\": [10, 20, 30]}]}\n" +
            "【【【\n"+
            "{根据数据分析可得，该网站用户数量逐日增长，时间越长，用户数量增长越多。】】】}";



    private String getGenerateQuestionUserMessage(Chart chart) {
        String goal = chart.getGoal();
        String ChartType = chart.getChatType();
        String csvData = chart.getChartdata();
        //构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");
        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(ChartType)) {
            userGoal += "，请使用" + ChartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData);
        return userInput.toString();
    }
    /**
     * 智能分析（异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<AiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws InterruptedException {
        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        //redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());
        Chart chart = chartService.getChartTask(multipartFile, genChartByAiRequest, loginUser);
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + chart.getId());
        try {
            boolean res = lock.tryLock(3, 15, TimeUnit.SECONDS);
            // 没抢到锁，强行返回
            if (!res) {
                return null;
            }
        // 建议处理任务队列满了后，抛异常的情况
        try {
            CompletableFuture.runAsync(() -> {
                // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果；执行失败后，状态修改为 “失败”，记录任务失败信息。
                Chart updateChart = new Chart();
                updateChart.setId(chart.getId());
                updateChart.setStatus(ChartConstant.RUNNING);
                boolean b = chartService.updateById(updateChart);
                if (!b) {
                    handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                    return;
                }
                // 调用 AI
                String string = aiManager.doSyncUnstableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, getGenerateQuestionUserMessage(chart));
                // 保存结果
                BiResponse saveChartAiResult = chartService.saveChartAiResult(string, chart.getId());
                if (saveChartAiResult == null) {
                    chartService.handleChartUpdateError(chart.getId(), "保存图表AI结果失败");
                }
            }, threadPoolExecutor);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"系统繁忙，请稍后重试");
        }
        } finally {
            if (lock != null && lock.isLocked()) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
        AiResponse aiResponse = new AiResponse();
        aiResponse.setResultId(chart.getId());
        //返回数据参数
        return ResultUtils.success(aiResponse);
    }

    /**
     * 智能分析（异步消息队列）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<AiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                        GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        //限流
        redisLimiterManager.doRateLimit("doRateLimit_" + loginUser.getId());
        //获取任务表数据
        Chart chartTask = chartService.getChartTask(multipartFile, genChartByAiRequest, loginUser);
        Long chartId = chartTask.getId();
        log.warn("准备发送信息给队列，Message={}=======================================",chartId);
        mqMessageProducer.sendMessage(MqConstant.BI_EXCHANGE_NAME,MqConstant.BI_ROUTING_KEY,String.valueOf(chartId));
        //返回数据参数
        AiResponse aiResponse = new AiResponse();
        aiResponse.setResultId(chartTask.getId());
        return ResultUtils.success(aiResponse);

    }


    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage("execMessage");
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }


    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChatType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


}
