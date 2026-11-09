package fun.commons.template4j.site.smoke;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import fun.commons.template4j.site.SiteApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = SiteApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationStartupIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // context loaded successfully
    }

    @SuppressWarnings("unchecked")
    @Test
    void engineEndpointResponds() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/engine", Map.class);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        Map<String, Object> body = resp.getBody();
        assertEquals(0, body.get("code"));
        assertNotNull(body.get("data"));
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        assertEquals("ok", data.get("status"));
        assertNotNull(data.get("components"));
        assertEquals("0.4.0", data.get("version"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void engineEndpointIncludesTraceId() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/engine", Map.class);
        assertNotNull(resp.getBody());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body.get("trace_id"));
        String traceId = (String) body.get("trace_id");
        assertFalse(traceId.isEmpty());
    }
}
