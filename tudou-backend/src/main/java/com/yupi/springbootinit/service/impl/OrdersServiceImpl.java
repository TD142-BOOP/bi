package com.yupi.springbootinit.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.model.entity.Orders;
import com.yupi.springbootinit.service.OrdersService;
import com.yupi.springbootinit.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

/**
* @author 86147
* @description 针对表【orders(充值订单表)】的数据库操作Service实现
* @createDate 2024-06-04 15:21:22
*/
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders>
    implements OrdersService{

}




