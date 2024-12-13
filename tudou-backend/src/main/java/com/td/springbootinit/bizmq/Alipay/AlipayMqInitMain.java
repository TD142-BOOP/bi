package com.td.springbootinit.bizmq.Alipay;

import com.td.springbootinit.constant.MqConstant;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建程序的交换机和队列
 */
@Configuration
public class AlipayMqInitMain {

    /**
     * 将死信队列和交换机声明
     */
    @Bean
    Queue AlipayDeadQueue(){
        return QueueBuilder.durable(MqConstant.ORDERS_DEAD_QUEUE_NAME).build();
    }

    @Bean
    DirectExchange AlipayDeadExchange() {
        return new DirectExchange(MqConstant.ORDERS_DEAD_EXCHANGE_NAME);
    }


    @Bean
    Binding AlipayDeadAlipaynding(Queue AlipayDeadQueue, DirectExchange AlipayDeadExchange) {
        return BindingBuilder.bind(AlipayDeadQueue).to(AlipayDeadExchange).with(MqConstant.ORDERS_DEAD_ROUTING_KEY);
    }

    /**
     * 将队列和交换机声明
     */
    @Bean
    Queue AliPayQueue(){
        //信息参数 设置TTL为1min
        Map<String,Object> arg = new HashMap<>();
        arg.put("x-message-ttl",60000);
        //绑定死信交换机
        arg.put("x-dead-letter-exchange",MqConstant.ORDERS_DEAD_EXCHANGE_NAME);
        arg.put("x-dead-letter-routing-key",MqConstant.ORDERS_DEAD_ROUTING_KEY);
        return QueueBuilder.durable(MqConstant.ORDERS_QUEUE_NAME).withArguments(arg).build();
    }

    @Bean
    DirectExchange AliPayExchange() {
        return new DirectExchange(MqConstant.ORDERS_EXCHANGE_NAME);
    }

    @Bean
    Binding BiAlipaynding(Queue AliPayQueue, DirectExchange AliPayExchange) {
        return BindingBuilder.bind(AliPayQueue).to(AliPayExchange).with(MqConstant.ORDERS_ROUTING_KEY);
    }


}