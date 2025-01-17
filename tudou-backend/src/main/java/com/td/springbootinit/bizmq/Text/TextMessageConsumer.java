package com.td.springbootinit.bizmq.Text;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.constant.MqConstant;
import com.td.springbootinit.constant.TextConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.manager.AiManager;
import com.td.springbootinit.model.entity.TextRecord;
import com.td.springbootinit.model.entity.TextTask;
import com.td.springbootinit.service.TextRecordService;
import com.td.springbootinit.service.TextTaskService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 文本转换消费者队列
 */
@Component
@Slf4j
public class TextMessageConsumer {

    @Resource
    private TextTaskService textTaskService;

    @Resource
    private TextRecordService textRecordService;
    @Resource
    private AiManager aiManager;
    private static final String GENERATE_QUESTION_SYSTEM_MESSAGE = "你是一个数据格式转换高手，接下来我会按照以下固定格式给你提供内容：\n" +
            "```\n" +
            "请使用\n" +
            "【【【文本类型】】】\n" +
            "语法对下面文章格式化\n" +
            "```\n" +
            "\n" +
            "请根据这两部分内容，按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
            "【【【\n"+
            "{\ncontent\n=\n# 123\n}\n" +
            "【【【\n"+
            "\n";
    @SneakyThrows
    @RabbitListener(queues = {MqConstant.TEXT_QUEUE_NAME},ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.warn("接收到队列信息，receiveMessage={}=======================================",message);
        if (StringUtils.isBlank(message)){
            //消息为空，消息拒绝，不重复发送，不重新放入队列
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }

        long textTaskId = Long.parseLong(message);
        List<TextRecord> textRecordList = textRecordService.list(new QueryWrapper<TextRecord>().eq("textTaskId", textTaskId));
        TextTask textTask = textTaskService.getById(textTaskId);
        if (textRecordList == null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文本为空");
        }
        //修改表状态为执行中，执行成功修改为“已完成”；执行失败修改为“失败”
        TextTask updateTask = new TextTask();
        updateTask.setId(textTaskId);
        updateTask.setStatus(TextConstant.RUNNING);
        boolean updateResult = textTaskService.updateById(updateTask);
        if (!updateResult){
            textTaskService.handleTextTaskUpdateError(textTaskId,"更新图表执行状态失败");
            return;
        }
        //调用AI

        for (TextRecord textRecord : textRecordList) {
            String result = null;
            //队列重新消费时，不在重新生成已经生成过的数据
            if (textRecord.getGenTextContent() != null) continue;
            try {
                result = aiManager.doSyncUnstableRequest(GENERATE_QUESTION_SYSTEM_MESSAGE,textRecordService.buildUserInput(textRecord,textTask.getTextType()));
            } catch (Exception e) {
                channel.basicNack(deliveryTag,false,true);
                log.warn("信息放入队列{}", DateTime.now());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"AI 服务错误");
            }
            textRecord.setGenTextContent(result);
            textRecord.setStatus(TextConstant.SUCCEED);
            boolean updateById = textRecordService.updateById(textRecord);
            if (!updateById){
                log.warn("AI生成错误，重新放入队列");
                channel.basicNack(deliveryTag,false,true);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,"保存失败");
            }
        }
        //将记录表中已经生成好的内容合并存入任务表
        StringBuilder stringBuilder = new StringBuilder();
        textRecordList.forEach(textRecord1 -> {
            stringBuilder.append(textRecord1.getGenTextContent()).append('\n');
        });
        TextTask textTask1 = new TextTask();
        textTask1.setId(textTaskId);
        int start = stringBuilder.indexOf("#");
        int end = stringBuilder.lastIndexOf("}");
        textTask1.setGenTextContent(stringBuilder.substring(start,end));
        textTask1.setStatus(TextConstant.SUCCEED);
        boolean save = textTaskService.updateById(textTask1);
        if (!save){
            channel.basicNack(deliveryTag,false,true);
            textTaskService.handleTextTaskUpdateError(textTask.getId(), "ai返回文本任务保存失败");
        }

        //消息确认
        channel.basicAck(deliveryTag,false);
    }


}