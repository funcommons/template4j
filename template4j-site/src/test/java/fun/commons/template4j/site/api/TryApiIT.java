package fun.commons.template4j.demo.api;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
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
}
