package fun.commons.template4j.site.config;

import fun.commons.template4j.site.SiteApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TraceIdFilter 边界: 各种 X-Trace-Id 头部格式 — valid / null / empty / 含非法字符 / 超长。
 */
@SpringBootTest(classes = SiteApplication.class)
@AutoConfigureMockMvc
class TraceIdFilterCoverageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noHeaderGeneratesUuid() throws Exception {
        mockMvc.perform(get("/api/learn"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER));
    }

    @Test
    void validHeaderIsPreserved() throws Exception {
        mockMvc.perform(get("/api/learn").header(TraceIdFilter.TRACE_ID_HEADER, "my-trace-id-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, "my-trace-id-1"));
    }

    @Test
    void emptyHeaderGeneratesUuid() throws Exception {
        mockMvc.perform(get("/api/learn").header(TraceIdFilter.TRACE_ID_HEADER, ""))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(result -> {
                    String v = result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
                    assert !v.isEmpty();
                });
    }

    @Test
    void invalidCharsInHeaderRegenerates() throws Exception {
        // 含空格 / 中文 → 不匹配 [A-Za-z0-9_-] → 重新生成
        mockMvc.perform(get("/api/learn").header(TraceIdFilter.TRACE_ID_HEADER, "bad trace id!!!"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(result -> {
                    String v = result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
                    assert !v.equals("bad trace id!!!");
                });
    }

    @Test
    void tooLongHeaderRegenerates() throws Exception {
        // 65 字符 → 不匹配 {1,64}
        String longId = "a".repeat(65);
        mockMvc.perform(get("/api/learn").header(TraceIdFilter.TRACE_ID_HEADER, longId))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String v = result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
                    assert v.length() == 32 : "expected regenerated 32-char uuid, got " + v.length();
                });
    }
}
