package fun.commons.template4j.site.api;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import fun.commons.template4j.site.SiteApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SiteApplication.class)
@AutoConfigureMockMvc
class TryApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tryBasic() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"hello\"}");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.rendered.msg").value("hello"))
            .andExpect(jsonPath("$.data.timing_ms").isNumber());
    }

    @Test
    void tryWithTrace() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"hello\"}");
        body.put("data", Map.of());
        JSONObject options = new JSONObject();
        options.put("with_trace", true);
        body.put("options", options);

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.trace").isArray());
    }

    @Test
    void tryEmptyTemplate() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(10400));
    }

    @Test
    void tryYamlTemplate() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "yaml");
        body.put("template", "msg: hello yaml");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void tryWithOptionsButNoWithTrace() throws Exception {
        // options 存在但 with_trace 缺失 → withTrace()=false → trace 为空列表
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"hi\"}");
        body.put("data", Map.of());
        JSONObject options = new JSONObject();
        // 不设 with_trace
        body.put("options", options);

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.trace").isArray())
            .andExpect(jsonPath("$.data.trace.length()").value(0));
    }

    @Test
    void tryWithWithTraceFalse() throws Exception {
        // options.with_trace=false → 显式 false
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"hi\"}");
        body.put("data", Map.of());
        JSONObject options = new JSONObject();
        options.put("with_trace", false);
        body.put("options", options);

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.trace.length()").value(0));
    }

    @Test
    void tryNullTemplateTypeDefaults() throws Exception {
        // template_type 缺失 → null → 走 default json
        JSONObject body = new JSONObject();
        // 不设 template_type
        body.put("template", "{\"msg\":\"hi\"}");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void tryExplicitNullOptions() throws Exception {
        // options 显式为 null → withTrace=false → trace 空数组
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"hi\"}");
        body.put("data", Map.of());
        body.put("options", null);  // 显式 null

        mockMvc.perform(post("/api/try")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.trace.length()").value(0));
    }
}
