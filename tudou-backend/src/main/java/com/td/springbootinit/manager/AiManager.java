package com.td.springbootinit.manager;

import com.td.springbootinit.common.ErrorCode;
import com.td.springbootinit.exception.BusinessException;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import io.reactivex.Flowable;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.ArrayList;

/**
 * 用于对接 AI 平台
 */
@Service
public class AiManager {

    @Resource
    private ClientV4 clientV4;
    // 较稳定的随机数
    private static final float STABLE_TEMPERATURE = 0.05f;

    // 不稳定的随机数
    private static final float UNSTABLE_TEMPERATURE = 0.99f;

    /**
     * 同步调用（答案较稳定）
     *
     * @param systemMessage
     * @param userMessage
     * @return
     */
    public String doSyncStableRequest(String systemMessage, String userMessage) {
        return doSyncRequest(systemMessage, userMessage, STABLE_TEMPERATURE);
    }

    /**
     * 同步调用（答案较随机）
     *
     * @param systemMessage
     * @param userMessage
     * @return
     */
    public String doSyncUnstableRequest(String systemMessage, String userMessage) {
        return doSyncRequest(systemMessage, userMessage, UNSTABLE_TEMPERATURE);
    }


    public String doSyncRequest(String systemMessage, String userMessage, Float temperature) {
        return doRequest(systemMessage, userMessage, Boolean.FALSE, temperature);
    }

    public String doRequest(String systemMessage, String userMessage, Boolean isStream, Float temperature) {
        ArrayList<ChatMessage> messageList = new ArrayList<>();
        ChatMessage chatSystemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);
        messageList.add(chatSystemMessage);
        ChatMessage chatUserMessage = new ChatMessage(ChatMessageRole.USER.value(), userMessage);
        messageList.add(chatUserMessage);
        return doRequest(messageList, isStream, temperature);
    }

    public String doRequest(ArrayList<ChatMessage> messageList, Boolean isStream, Float temperature) {
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4)
                .stream(isStream)
                .invokeMethod(Constants.invokeMethod)
                .messages(messageList)
                .temperature(temperature)
                .build();
        try {
            ModelApiResponse modelApiResponse = clientV4.invokeModelApi(chatCompletionRequest);
            return modelApiResponse.getData().getChoices().get(0).getMessage().toString();
        } catch (Exception e) {
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }
    public Flowable<ModelData> doStreamRequest(String systemMessage, String userMessage, Float temperature) {
        ArrayList<ChatMessage> messageList = new ArrayList<>();
        ChatMessage chatSystemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage);
        messageList.add(chatSystemMessage);
        ChatMessage chatUserMessage = new ChatMessage(ChatMessageRole.USER.value(), userMessage);
        messageList.add(chatUserMessage);
        return doStreamRequest(messageList, temperature);
    }

    public Flowable<ModelData> doStreamRequest(ArrayList<ChatMessage> messageList, Float temperature) {
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4)
                .stream(Boolean.TRUE)
                .invokeMethod(Constants.invokeMethod)
                .messages(messageList)
                .temperature(temperature)
                .build();
        ModelApiResponse modelApiResponse = clientV4.invokeModelApi(chatCompletionRequest);
        return modelApiResponse.getFlowable();
    }
}
