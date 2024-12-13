package com.td.springbootinit.common;


import com.td.springbootinit.manager.AiManager;

import com.td.springbootinit.model.dto.chart.GenChartByAiRequest;
import com.td.springbootinit.model.entity.Chart;
import com.td.springbootinit.model.entity.User;

import com.td.springbootinit.model.vo.BiResponse;
import com.td.springbootinit.service.ChartService;
import com.td.springbootinit.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;


import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.concurrent.TimeUnit;


@Component

public class AiCommon {
    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedissonClient redissonClient;

//    public BiResponse getAiCommon(MultipartFile multipartFile,
//                                  GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws InterruptedException {
//        User loginUser = userService.getLoginUser(request);
//        Chart chart = chartService.getChartTask(multipartFile, genChartByAiRequest, loginUser);
//        String AI_ANSWER_LOCK = "AI_CHART_LOCK";
//        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + chart.getId());
//        try {
//            boolean res = lock.tryLock(3, 15, TimeUnit.SECONDS);
//            // 没抢到锁，强行返回
//            if (!res) {
//                return null;
//            }
//            String result = aiManager.doChat(CommonConstant.BI_MODEL_ID, chartService.buildUserInput(chart));
//            //处理返回的数据
//            return chartService.saveChartAiResult(result, chart.getId());
//        } finally {
//            if (lock != null && lock.isLocked()) {
//                if (lock.isHeldByCurrentThread()) {
//                    lock.unlock();
//                }
//            }
//        }
//
//    }
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
    public BiResponse getAiCommon(MultipartFile multipartFile,
                                  GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws InterruptedException {
        User loginUser = userService.getLoginUser(request);
        Chart chart = chartService.getChartTask(multipartFile, genChartByAiRequest, loginUser);
        String AI_ANSWER_LOCK = "AI_CHART_LOCK";
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + chart.getId());
        try {
            boolean res = lock.tryLock(3, 15, TimeUnit.SECONDS);
            // 没抢到锁，强行返回
            if (!res) {
                return null;
            }
            String result = aiManager.doSyncUnstableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, getGenerateQuestionUserMessage(chart));
            //处理返回的数据
            return chartService.saveChartAiResult(result, chart.getId());
        } finally {
            if (lock != null && lock.isLocked()) {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }

    }
//    public SseEmitter getAiCommon(MultipartFile multipartFile,
//                                  GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) throws InterruptedException {
//        User loginUser = userService.getLoginUser(request);
//        Chart chart = chartService.getChartTask(multipartFile, genChartByAiRequest, loginUser);
//        String AI_ANSWER_LOCK = "AI_CHART_LOCK";
//        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK + chart.getId());
//        SseEmitter sseEmitter = new SseEmitter(0L);
//        Flowable<ModelData> modelDataFlowable = aiManager.doStreamRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, getGenerateQuestionUserMessage(chart), null);
//        AtomicInteger atomicInteger = new AtomicInteger(0);
//        StringBuilder stringBuilder = new StringBuilder();
//        Scheduler scheduler = Schedulers.io();
//        modelDataFlowable
//                .observeOn(scheduler)
//                .map(chunk -> chunk.getChoices().get(0).getdelta().getContent())
//                .map(message -> message.replaceAll("\\s", ""))
//                .filter(StrUtil::isNotBlank)
//                .flatMap(message -> {
//                    ArrayList<Character> charList = new ArrayList<>();
//                    for (char c : message.toCharArray()) {
//                        charList.add(c);
//                    }
//                    return Flowable.fromIterable(charList);
//                }).doOnNext(c -> {
//                    {
//                        if (c == '{') {
//                            atomicInteger.addAndGet(1);
//                        }
//                        if (atomicInteger.get() > 0) {
//                            stringBuilder.append(c);
//                        }
//                        if (c == '}') {
//                            atomicInteger.addAndGet(-1);
//                            if (atomicInteger.get() == 0) {
//                                sseEmitter.send(JSONUtil.toJsonStr(stringBuilder.toString()));
//                                stringBuilder.setLength(0);
//                            }
//                        }
//                    }
//                }).doOnComplete(sseEmitter::complete).subscribe();
//        // 结果处理
//        //处理返回的数据
//        return sseEmitter;
//    }
    }
