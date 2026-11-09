package fun.commons.template4j.demo.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import fun.commons.template4j.demo.DemoApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DemoApplication.class)
@AutoConfigureMockMvc
class GlobalExceptionHandlerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handlerReturnsErrorEnvelope() throws Exception {
        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not valid json"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10500))
            .andExpect(jsonPath("$.error.type").value("internal_error"));
    }

    @Test
    void bizExceptionReturnsErrorCode() throws Exception {
        mockMvc.perform(get("/api/learn/invalid$id"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(10404));
    }
}
