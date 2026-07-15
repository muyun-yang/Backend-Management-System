// // src/main/java/com/example/Service/DashScopeService.java
// package com.example.Service;

// import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgent;
// import com.alibaba.cloud.ai.dashscope.agent.DashScopeAgentOptions;
// import com.alibaba.cloud.ai.dashscope.api.DashScopeAgentApi;
// import org.springframework.ai.chat.messages.AssistantMessage;
// import org.springframework.ai.chat.model.ChatResponse;
// import org.springframework.ai.chat.prompt.Prompt;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// @Service
// public class DashScopeService {

//     private static final Logger logger = LoggerFactory.getLogger(DashScopeService.class);
//     private final DashScopeAgent agent;

//     @Value("${aliyun.bailian.app-id}")
//     private String appId;

//     public DashScopeService(DashScopeAgentApi dashscopeAgentApi) {
//         this.agent = new DashScopeAgent(dashscopeAgentApi);
//     }

//     public String call(String message) {
//         try {
//             ChatResponse response = agent.call(new Prompt(message, DashScopeAgentOptions.builder().withAppId(appId).build()));
//             if (response == null || response.getResult() == null) {
//                 logger.error("chat response is null");
//                 return "chat response is null";
//             }
//             AssistantMessage app_output = response.getResult().getOutput();
//             return app_output.getText();
//         } catch (Exception e) {
//             logger.error("Error calling DashScope agent with message: {}", message, e);
//             throw new RuntimeException("Error calling DashScope agent", e);
//         }
//     }
// }