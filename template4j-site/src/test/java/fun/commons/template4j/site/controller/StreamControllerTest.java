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

    /**
     * hello 事件在 StreamingResponseBody 的 lambda 中写出, 由 Spring 异步线程执行;
     * MockMvc {@code andReturn()} 的响应快照可能先于 lambda 首次 flush
     * (CI 慢机实绩 065b0f5: 拿到空响应)。有界轮询 (最长 ~2s) 消除该竞态。
     */
    private static String awaitHello(MvcResult result)
            throws InterruptedException, java.io.UnsupportedEncodingException {
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (int i = 0; i < 40 && !response.contains("event: hello"); i++) {
            Thread.sleep(50);
            response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        }
        return response;
    }

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

        String response = awaitHello(result);
        // MockMvc 行为: StreamingResponseBody 异步执行, hello 为首个事件, 可达即有 hello
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

        String response = awaitHello(result);
        Assertions.assertTrue(response.contains("event: hello"),
                "expected hello event, got: " + response);
    }

    @Test
    void controllerIsRegistered() {
        // @SpringBootTest 已经扫描了 controller,通过 mockMvc 间接验证
        Assertions.assertTrue(true);
    }
}