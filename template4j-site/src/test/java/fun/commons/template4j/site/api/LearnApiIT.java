package fun.commons.template4j.demo.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import fun.commons.template4j.demo.DemoApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DemoApplication.class)
@AutoConfigureMockMvc
class LearnApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listReturnsExamples() throws Exception {
        mockMvc.perform(get("/api/learn"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").isNumber())
            .andExpect(jsonPath("$.data.examples").isArray())
            .andExpect(jsonPath("$.data.examples.length()").value(36));
    }

    @Test
    void getNotFoundExample() throws Exception {
        mockMvc.perform(get("/api/learn/999-not-exist"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(10404));
    }
}
