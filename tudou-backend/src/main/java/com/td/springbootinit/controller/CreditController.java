package com.td.springbootinit.controller;
import java.util.Date;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.td.springbootinit.annotation.AuthCheck;
import com.td.springbootinit.common.BaseResponse;
import com.td.springbootinit.common.DeleteRequest;
import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.common.ResultUtils;
import com.td.springbootinit.constant.CommonConstant;
import com.td.springbootinit.constant.UserConstant;
import com.td.springbootinit.exception.BusinessException;
import com.td.springbootinit.exception.ThrowUtils;
import com.td.springbootinit.model.dto.credit.CreditAddRequest;
import com.td.springbootinit.model.dto.credit.CreditEditRequest;
import com.td.springbootinit.model.dto.credit.CreditQueryRequest;
import com.td.springbootinit.model.dto.credit.CreditUpdateRequest;
import com.td.springbootinit.model.entity.Credit;
import com.td.springbootinit.model.entity.User;
import com.td.springbootinit.service.CreditService;
import com.td.springbootinit.service.UserService;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import com.td.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子接口
 *
 
 */
@RestController
@RequestMapping("/credit")
@Slf4j
public class CreditController {

    @Resource
    private CreditService creditService;

    @Resource
    private UserService userService;

    // region 增删改查

    /**
     * 创建
     *
     * @param creditAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addCredit(@RequestBody CreditAddRequest creditAddRequest, HttpServletRequest request) {
        if (creditAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Credit credit = new Credit();
        BeanUtils.copyProperties(creditAddRequest, credit);
        Long userId = creditAddRequest.getUserId();
        credit.setUserId(userId);
        boolean result = creditService.save(credit);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newCreditId = credit.getId();
        return ResultUtils.success(newCreditId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteCredit(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Credit oldCredit = creditService.getById(id);
        ThrowUtils.throwIf(oldCredit == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldCredit.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = creditService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param creditUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateCredit(@RequestBody CreditUpdateRequest creditUpdateRequest) {
        if (creditUpdateRequest == null || creditUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Credit credit = new Credit();
        BeanUtils.copyProperties(creditUpdateRequest, credit);
        long id = creditUpdateRequest.getId();
        // 判断是否存在
        Credit oldCredit = creditService.getById(id);
        ThrowUtils.throwIf(oldCredit == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = creditService.updateById(credit);
        return ResultUtils.success(result);
    }


    /**
     * 分页获取列表（仅管理员）
     *
     * @param creditQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Credit>> listCreditByPage(@RequestBody CreditQueryRequest creditQueryRequest) {
        long current = creditQueryRequest.getCurrent();
        long size = creditQueryRequest.getPageSize();
        Page<Credit> creditPage = creditService.page(new Page<>(current, size),
                getQueryWrapper(creditQueryRequest));
        return ResultUtils.success(creditPage);
    }


    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param creditQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Credit>> listMyCreditVOByPage(@RequestBody CreditQueryRequest creditQueryRequest,
                                                         HttpServletRequest request) {
        if (creditQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        creditQueryRequest.setUserId(loginUser.getId());
        long current = creditQueryRequest.getCurrent();
        long size = creditQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Credit> creditPage = creditService.page(new Page<>(current, size),
                getQueryWrapper(creditQueryRequest));
        return ResultUtils.success(creditPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param creditEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editCredit(@RequestBody CreditEditRequest creditEditRequest, HttpServletRequest request) {
        if (creditEditRequest == null || creditEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Credit credit = new Credit();
        BeanUtils.copyProperties(creditEditRequest, credit);
        User loginUser = userService.getLoginUser(request);
        long id = creditEditRequest.getId();
        // 判断是否存在
        Credit oldCredit = creditService.getById(id);
        ThrowUtils.throwIf(oldCredit == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldCredit.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = creditService.updateById(credit);
        return ResultUtils.success(result);
    }

    @GetMapping("/sign")
    public BaseResponse<Boolean> signCredit(HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        Long userId = loginUser.getId();
        Boolean sign = creditService.getSign(userId);
        return ResultUtils.success(sign);
    }

    private QueryWrapper<Credit> getQueryWrapper(CreditQueryRequest creditQueryRequest){
        QueryWrapper<Credit> queryWrapper = new QueryWrapper<>();
        if(creditQueryRequest==null){
            return queryWrapper;
        }
        Long id = creditQueryRequest.getId();
        Long userId = creditQueryRequest.getUserId();
        Long creditTotal = creditQueryRequest.getCreditTotal();
        Date createTime = creditQueryRequest.getCreateTime();
        Date updateTime = creditQueryRequest.getUpdateTime();
        String sortField = creditQueryRequest.getSortField();
        String sortOrder = creditQueryRequest.getSortOrder();

        queryWrapper.eq(id!=null&&id>0,"id",id);
        queryWrapper.like(ObjectUtils.isNotEmpty(creditTotal),"creditTotal",creditTotal);
        queryWrapper.le(ObjectUtils.isNotEmpty(updateTime),"updateTime",updateTime);
        queryWrapper.le(ObjectUtils.isNotEmpty(createTime),"createTime",createTime);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId),"userId",userId);
        queryWrapper.eq("isDelete",false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),sortOrder.equals(CommonConstant.SORT_ORDER_ASC),sortOrder);
        return queryWrapper;
    }

}
