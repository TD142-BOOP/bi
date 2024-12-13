package com.td.springbootinit.controller;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.td.springbootinit.annotation.AuthCheck;
import com.td.springbootinit.bizmq.common.MqMessageProducer;
import com.td.springbootinit.common.BaseResponse;
import com.td.springbootinit.common.DeleteRequest;
import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.common.ResultUtils;
import com.td.springbootinit.constant.CommonConstant;
import com.td.springbootinit.constant.MqConstant;
import com.td.springbootinit.constant.TextConstant;
import com.td.springbootinit.constant.UserConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.exception.ThrowUtils;
import com.td.springbootinit.manager.AiManager;
import com.td.springbootinit.manager.RedisLimiterManager;
import com.td.springbootinit.model.dto.text.*;
import com.td.springbootinit.model.entity.TextRecord;
import com.td.springbootinit.model.entity.TextTask;
import com.td.springbootinit.model.entity.User;
import com.td.springbootinit.model.vo.AiResponse;
import com.td.springbootinit.model.vo.TextTaskVO;
import com.td.springbootinit.service.ChartService;
import com.td.springbootinit.service.TextRecordService;
import com.td.springbootinit.service.TextTaskService;
import com.td.springbootinit.service.UserService;
import com.td.springbootinit.utils.SqlUtils;
import com.zhipu.oapi.service.v4.model.ModelData;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 帖子接口
 *
 
 */
@RestController
@RequestMapping("/text")
@Slf4j
public class TextController {
    @Resource
    private TextTaskService textTaskService;
    @Resource
    private UserService userService;

    @Resource
    private MqMessageProducer mqMessageProducer;
    @Resource
    private RedisLimiterManager redisLimiterManager;

    // region 增删改查

    /**
     * 创建
     *
     * @param textAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addText(@RequestBody TextAddRequest textAddRequest, HttpServletRequest request) {
        if (textAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TextTask text = new TextTask();
        BeanUtils.copyProperties(textAddRequest, text);
        User loginUser = userService.getLoginUser(request);
        text.setUserId(loginUser.getId());
        boolean result = textTaskService.save(text);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newTextId = text.getId();
        return ResultUtils.success(newTextId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteText(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        TextTask oldText = textTaskService.getById(id);
        ThrowUtils.throwIf(oldText == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldText.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = textTaskService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param textUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateText(@RequestBody TextUpdateRequest textUpdateRequest) {
        if (textUpdateRequest == null || textUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TextTask textTask = new TextTask();
        BeanUtils.copyProperties(textUpdateRequest, textTask);
        long id = textUpdateRequest.getId();
        // 判断是否存在
        TextTask oldText = textTaskService.getById(id);
        ThrowUtils.throwIf(oldText == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = textTaskService.updateById(textTask);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<TextTask> getTextById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TextTask text = textTaskService.getById(id);
        if (text == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(text);
    }

    @GetMapping("/get/vo")
    public BaseResponse<TextTaskVO> getTextTaskVOById(long id, HttpServletRequest request){
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TextTask text = textTaskService.getById(id);
        if (text == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        TextTaskVO textTaskVO = new TextTaskVO();
        BeanUtils.copyProperties(text,textTaskVO);
        return ResultUtils.success(textTaskVO);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param textQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<TextTask>> listTextByPage(@RequestBody TextQueryRequest textQueryRequest,
                                                     HttpServletRequest request) {
        long current = textQueryRequest.getCurrent();
        long size = textQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<TextTask> textPage = textTaskService.page(new Page<>(current, size),
                getQueryWrapper(textQueryRequest));
        return ResultUtils.success(textPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param textQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<TextTask>> listMyTextByPage(@RequestBody TextQueryRequest textQueryRequest,
                                                       HttpServletRequest request) {
        if (textQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        textQueryRequest.setUserId(loginUser.getId());
        long current = textQueryRequest.getCurrent();
        long size = textQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<TextTask> textPage = textTaskService.page(new Page<>(current, size),
                getQueryWrapper(textQueryRequest));
        return ResultUtils.success(textPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param textEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editText(@RequestBody TextEditRequest textEditRequest, HttpServletRequest request) {
        if (textEditRequest == null || textEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        TextTask text = new TextTask();
        BeanUtils.copyProperties(textEditRequest, text);
        User loginUser = userService.getLoginUser(request);
        long id = textEditRequest.getId();
        // 判断是否存在
        TextTask oldText = textTaskService.getById(id);
        ThrowUtils.throwIf(oldText == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldText.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = textTaskService.updateById(text);
        return ResultUtils.success(result);
    }


    /**
     * 智能分析（异步消息队列）
     *
     * @param multipartFile
     * @param genTextByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<AiResponse> genTextByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                       GenTextByAiRequest genTextByAiRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genTextByAi_" + loginUser.getId());

        TextTask textTask = textTaskService.getTextTask(multipartFile, genTextByAiRequest, loginUser);
        Long taskId = textTask.getId();
        log.warn("准备发送信息给队列，Message={}=======================================",taskId);
        mqMessageProducer.sendMessage(MqConstant.TEXT_EXCHANGE_NAME,MqConstant.TEXT_ROUTING_KEY,taskId.toString());
        AiResponse aiResponse = new AiResponse();
        aiResponse.setResultId(taskId);
        return ResultUtils.success(aiResponse);
    }




    /**
     * 获取查询包装类
     *
     * @param textQueryRequest
     * @return
     */
    private QueryWrapper<TextTask> getQueryWrapper(TextQueryRequest textQueryRequest) {
        QueryWrapper<TextTask> queryWrapper = new QueryWrapper<>();
        if (textQueryRequest == null) {
            return queryWrapper;
        }
        Long id = textQueryRequest.getId();
        String name = textQueryRequest.getName();
        String textType = textQueryRequest.getTextType();
        Long userId = textQueryRequest.getUserId();
        String sortField = textQueryRequest.getSortField();
        String sortOrder = textQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(textType), "textType", textType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


}
