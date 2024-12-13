package com.td.springbootinit.bizmq.Bi;

import cn.hutool.core.date.DateTime;

import com.rabbitmq.client.Channel;
import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.constant.ChartConstant;
import com.td.springbootinit.constant.MqConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.manager.AiManager;
import com.td.springbootinit.model.entity.Chart;
import com.td.springbootinit.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 图表分析消费者队列
 */
@Component
@Slf4j
public class BiMessageConsumer {
//    @Resource
//    private RedissonClient redissonClient;
//    private final String AI_ANSWER_LOCK="AI_CHART_LOCK";
    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;
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
    @SneakyThrows
    @RabbitListener(queues = {MqConstant.BI_QUEUE_NAME},ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.warn("接收到队列信息，receiveMessage={}=======================================",message);
        if (StringUtils.isBlank(message)){
            //消息为空，消息拒绝，不重复发送，不重新放入队列
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图表为空");
        }

        //修改表状态为执行中，执行成功修改为“已完成”；执行失败修改为“失败”
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus(ChartConstant.RUNNING);
        boolean updateResult = chartService.updateById(updateChart);
        if (!updateResult){
            handleChartUpdateError(chart.getId(),"更新图表执行状态失败");
            return;
        }
        //调用AI
        String result = null;
        try {
                result = aiManager.doSyncUnstableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE, getGenerateQuestionUserMessage(chart));
        } catch (Exception e) {
            channel.basicNack(deliveryTag,false,true);
            log.warn("信息放入队列{}", DateTime.now());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 服务错误");
        }
        //处理返回的数据
        try {
            chartService.saveChartAiResult(result, chart.getId());
        } catch (Exception e) {
            //重新放回队列
            channel.basicNack(deliveryTag,false,true);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图表数据保存失败");
        }
        //消息确认
        channel.basicAck(deliveryTag,false);
    }
    private void handleChartUpdateError(Long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setStatus(ChartConstant.FAILED);
        updateChartResult.setId(chartId);
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult){
            log.error("更新图片失败状态失败"+chartId+","+execMessage);
        }
    }
}