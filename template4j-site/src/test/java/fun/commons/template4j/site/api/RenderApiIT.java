package fun.commons.template4j.demo.api;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.demo.config.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import fun.commons.template4j.demo.DemoApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DemoApplication.class)
@AutoConfigureMockMvc
class RenderApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void renderJsonBasic() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"{@user.name}\"}");
        JSONObject data = new JSONObject();
        JSONObject user = new JSONObject();
        user.put("name", "template4j");
        data.put("user", user);
        body.put("data", data);

        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.rendered.msg").value("template4j"));
    }

    @Test
    void renderInvalidJsonFallsBackToString() throws Exception {
        // templates such as {"foo": "{@x}"} can't be JSON-parsed cleanly because fastjson
        // chokes on {@...} inside string values. The service falls back to a string-mode
        // render so authors can mix placeholders into JSON-shaped templates.
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "not a json");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.rendered").value("not a json"))
            .andExpect(jsonPath("$.data.template_type").value("string"));
    }

    @Test
    void renderEmptyTemplate() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(10400));
    }

    @Test
    void renderTraceIdPropagated() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"msg\":\"hello\"}");
        body.put("data", Map.of());

        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Trace-Id", "test-trace-123")
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace_id").value("test-trace-123"));
    }

    @Test
    void renderYamlTemplate() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "yaml");
        body.put("template", "msg: \"{@user.name}\"");
        JSONObject data = new JSONObject();
        JSONObject user = new JSONObject();
        user.put("name", "yaml-test");
        data.put("user", user);
        body.put("data", data);

        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    /** RenderRequest.data now accepts any JSON shape (Object), not just Map<String,Object>.
     *  Previously Jackson rejected arrays with 500. */
    @Test
    void renderWithArrayData() throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req = post("/api/render")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"template_type\":\"json\",\"template\":\"{\\\"count\\\":1}\",\"data\":[\"a\",\"b\",\"c\"]}");
        mockMvc.perform(req)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void renderWithScalarData() throws Exception {
        // data = 42 (a bare integer). The service wraps scalars under "value".
        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"template_type\":\"string\",\"template\":\"value={\", \"data\": 42}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void renderWithNullData() throws Exception {
        JSONObject body = new JSONObject();
        body.put("template_type", "json");
        body.put("template", "{\"ok\":true}");
        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.toJSONString(body)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }
}
