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
 * 图表分析消费者队列
 */
@Component
@Slf4j
public class AlipayMessageConsumer {

    @Resource
    private OrdersService ordersService;

    @SneakyThrows
    @RabbitListener(queues = {MqConstant.ORDERS_QUEUE_NAME},ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.warn("接收到队列信息，receiveMessage={}=======================================",message);
        if (StringUtils.isBlank(message)){
            //消息为空，消息拒绝，不重复发送，不重新放入队列
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        long ordersId = Long.parseLong(message);
        Orders orders = ordersService.getById(ordersId);
        if (orders == null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        String tradeStatus = orders.getTradeStatus();
        log.warn("订单查询为"+orders.getTradeStatus());
        if(!tradeStatus.equals(OrdersConstant.SUCCEED)){
            log.warn("订单未支付成功,重新放回队列,订单号为"+orders.getId());
        }else {
            //消息确认
            channel.basicAck(deliveryTag, false);
        }
    }
}