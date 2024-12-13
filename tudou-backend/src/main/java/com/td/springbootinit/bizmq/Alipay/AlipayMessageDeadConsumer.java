package com.td.springbootinit.bizmq.Alipay;

import com.rabbitmq.client.Channel;
import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.constant.OrdersConstant;
import com.td.springbootinit.constant.MqConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.model.entity.Orders;
import com.td.springbootinit.service.OrdersService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 图表分析队列的死信队列
 */
@Component
@Slf4j
public class AlipayMessageDeadConsumer {

    @Resource
    private OrdersService ordersService;


    @SneakyThrows
    @RabbitListener(queues = {MqConstant.ORDERS_DEAD_QUEUE_NAME},ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.warn("接收到死信队列信息，receiveMessage={}=======================================",message);
        if (StringUtils.isBlank(message)){
            //消息为空，消息拒绝，不重复发送，不重新放入队列
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        long ordersId = Long.parseLong(message);
        Orders orders = ordersService.getById(ordersId);
        if (orders == null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图表为空");
        }

        //修改表状态为执行中，执行成功修改为“已完成”；执行失败修改为“失败”
        Orders updateOrders = new Orders();
        updateOrders.setId(orders.getId());
        updateOrders.setTradeStatus(OrdersConstant.FAILED);
        boolean updateResult = ordersService.updateById(updateOrders);
        if (!updateResult){
            handleOrdersUpdateError(orders.getId(),"更新图表执行状态失败");
            return;
        }
        //消息确认
        channel.basicAck(deliveryTag,false);
    }
    private void handleOrdersUpdateError(Long ordersId, String execMessage) {
        Orders updateOrdersResult = new Orders();
        updateOrdersResult.setTradeStatus(OrdersConstant.FAILED);
        updateOrdersResult.setId(ordersId);
        boolean updateResult = ordersService.updateById(updateOrdersResult);
        if (!updateResult){
            log.error("更新图片失败状态失败"+ordersId+","+execMessage);
        }
    }
}