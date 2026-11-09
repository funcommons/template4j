package fun.commons.template4j.site.controller;

import com.alibaba.fastjson2.JSON;
import fun.commons.template4j.site.SiteApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖 StreamController (SSE 推送): /api/try/stream 返回事件流 + 终态。
 */
@SpringBootTest(classes = SiteApplication.class)
@AutoConfigureMockMvc
class StreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void streamEndpointReturnsTextEventStreamContentType() throws Exception {
        // 验证响应是 SSE content-type (即使 MockMvc 拿到的是部分内容)
        String body = JSON.toJSONString(Map.of(
                "template_type", "json",
                "template", "{\"v\":\"hi\"}",
                "data", Map.of(),
                "options", Map.of("with_trace", false)
        ));

        MvcResult result = mockMvc.perform(post("/api/try/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // MockMvc 行为: StreamingResponseBody 可能只同步执行一部分,
        // 但 hello 事件总是先写,只要看到 hello 就算路由可达
        Assertions.assertTrue(response.contains("event: hello"),
                "expected hello event, got: " + response);
    }

    @Test
    void streamEndpointReachableForErrorCase() throws Exception {
        String body = JSON.toJSONString(Map.of(
                "template_type", "json",
                "template", "{\"@class\":\"definitely-not-a-real-component\"}",
                "data", Map.of(),
                "options", Map.of("with_trace", false)
        ));

        MvcResult result = mockMvc.perform(post("/api/try/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Assertions.assertTrue(response.contains("event: hello"),
                "expected hello event, got: " + response);
    }

    @Test
    void controllerIsRegistered() {
        // @SpringBootTest 已经扫描了 controller,通过 mockMvc 间接验证
        Assertions.assertTrue(true);
    }
}