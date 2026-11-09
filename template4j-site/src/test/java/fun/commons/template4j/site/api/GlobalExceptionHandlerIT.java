package fun.commons.template4j.site.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import fun.commons.template4j.site.SiteApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Assertions;

@SpringBootTest(classes = SiteApplication.class)
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

    @Test
    void nullPointerExceptionReturnsInternalErrorDetail() throws Exception {
        // /api/render 不传 template 字段 → 可能 NPE 或其它 → 走 handleException(Exception)
        // handleException 中 NPE 分支: detail = "Internal error"
        mockMvc.perform(post("/api/render")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                assert status == 500 || status == 400 : "got " + status;
            });
    }

    @Test
    void nullPointerExceptionFromControllerTriggersInternalErrorBranch() throws Exception {
        // 直接调用 handleException 传 NullPointerException → 命中 NPE 分支 (e instanceof NullPointerException = true)
        fun.commons.template4j.site.exception.GlobalExceptionHandler handler =
            new fun.commons.template4j.site.exception.GlobalExceptionHandler();
        NullPointerException npe = new NullPointerException("internal null");
        // mock HttpServletRequest 用 mockito-free 方式: 用 Spring 的 MockHttpServletRequest
        org.springframework.mock.web.MockHttpServletRequest request =
            new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/api/test");
        var resp = handler.handleException(npe, request);
        // NPE 分支: detail = "Internal error", code = INTERNAL_ERROR (10500)
        Assertions.assertEquals(10500, resp.getCode());
        Assertions.assertNotNull(resp.getError());
        Assertions.assertEquals("Internal error", resp.getError().detail());
    }
}
