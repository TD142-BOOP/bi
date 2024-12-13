//package com.td.springbootinit.controller;
//
//import cn.hutool.json.JSONObject;
//import com.alipay.api.AlipayApiException;
//import com.alipay.api.AlipayClient;
//import com.alipay.api.internal.util.AlipaySignature;
//import com.alipay.api.request.AlipayTradePagePayRequest;
//import com.td.springbootinit.bizmq.common.MqMessageProducer;
//import com.td.springbootinit.common.ErrorCode;
//import com.td.springbootinit.config.AliPayConfig;
//import com.td.springbootinit.constant.MqConstant;
//import com.td.springbootinit.constant.OrdersConstant;
//import com.td.springbootinit.exception.ThrowUtils;
//import com.td.springbootinit.model.dto.alipay.AlipayAddRequest;
//import com.td.springbootinit.model.entity.Orders;
//import com.td.springbootinit.model.entity.User;
//import com.td.springbootinit.service.CreditService;
//import com.td.springbootinit.service.OrdersService;
//import com.td.springbootinit.service.UserService;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.annotation.Resource;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//
//import static redis.clients.jedis.Protocol.CHARSET;
//
//@RestController
//@RequestMapping("/alipay")
//public class AlipayController {
//    @Resource
//    private UserService userService;
//    @Resource
//    private AlipayClient alipayClient;
//    @Resource
//    private OrdersService ordersService;
//    @Resource
//    private MqMessageProducer mqMessageProducer;
//    @Resource
//    private AliPayConfig aliPayConfig;
//    @Resource
//    private CreditService creditService;
//
//
//    @GetMapping("/pay")
//    public void pay(HttpServletRequest request, HttpServletResponse response, AlipayAddRequest alipayAddRequest) throws AlipayApiException, IOException {
//        User loginUser = userService.getLoginUser(request);
//
//        Orders orders = new Orders();
//        orders.setTotalAmount(alipayAddRequest.getTotalAmount());
//        orders.setSubject(alipayAddRequest.getSubject());
//        orders.setUserId(loginUser.getId());
//        boolean result = ordersService.save(orders);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"保存订单失败");
//
//        AlipayTradePagePayRequest alipayTradePagePayRequest = new AlipayTradePagePayRequest();
//        alipayTradePagePayRequest.setNotifyUrl(OrdersConstant.NOTIFYURL);
//        JSONObject bizContent = new JSONObject();
//        bizContent.set("out_trade_no",orders.getId());
//        bizContent.set("total_amount", orders.getTotalAmount()); // 订单的总金额
//        bizContent.set("subject", orders.getSubject());   // 支付的名称
//        bizContent.set("product_code", "FAST_INSTANT_TRADE_PAY");  // 固定配置
//        alipayTradePagePayRequest.setBizContent(bizContent.toString());
//
//        String from="";
//        try {
//            from=alipayClient.pageExecute(alipayTradePagePayRequest).getBody();
//        }catch (AlipayApiException e){
//            e.printStackTrace();
//        }
//        mqMessageProducer.sendMessage(MqConstant.ORDERS_EXCHANGE_NAME,MqConstant.ORDERS_ROUTING_KEY,orders.getId().toString());
//        response.setContentType("text/html;charset=" + CHARSET);
//        response.getWriter().write(from);
//        response.getWriter().flush();
//        response.getWriter().close();
//
//    }
//
//    @PostMapping("/notify")
//    public String payNotify(HttpServletRequest request) throws AlipayApiException {
//        if(request.getParameter("trade_status").equals("TRADE_SUCCESS")){
//            System.out.println("=========支付宝异步回调========");
//            Map<String,String> params = new HashMap<>();
//            HashMap<String,String[]> requestParams = new HashMap<>();
//            for(String name:requestParams.keySet()){
//                params.put(name,request.getParameter(name));
//            }
//            String outTradeNo = params.get("out_trade_no");
//            String buyerId = params.get("buyer_id");
//            String alipayTradeNo = params.get("trade_no");
//            //给金额转型
//            String[] total_amounts = params.get("total_amount").split("\\.");
//            Integer totalAmount = Integer.valueOf(total_amounts[0]);
//
//            String sign = params.get("sign");
//            String content = AlipaySignature.getSignCheckContentV1(params);
//            boolean checkSignature = AlipaySignature.rsa256CheckContent(content, sign, aliPayConfig.getAlipayPublicKey(), "UTF-8"); // 验证签名
//
//            if(checkSignature){
//                Orders orders = ordersService.getById(outTradeNo);
//                orders.setTradeStatus(OrdersConstant.SUCCEED);
//                orders.setAlipayTradeNo(alipayTradeNo);
//                orders.setBuyerId(buyerId);
//                boolean result = ordersService.updateById(orders);
//                ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR);
//                boolean result1 = creditService.updateCredits(orders.getId(), 100L * totalAmount);
//                ThrowUtils.throwIf(!result1,ErrorCode.SYSTEM_ERROR,"积分更新错误");
//            }
//        }
//        return "success";
//    }
//}
