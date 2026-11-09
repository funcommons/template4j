package fun.commons.template4j.site.controller;

import fun.commons.template4j.site.SiteApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 覆盖 ParallelController mock /slow-log 路径 (lambda$startMock$3) 与
 * stopMock null 分支。
 */
@SpringBootTest(classes = SiteApplication.class)
class ParallelControllerCoverageTest {

    @Autowired
    private ParallelController controller;

    @Test
    void slowLogEndpointDirectHit() throws Exception {
        // 直接访问 mock 的 /slow-log, 让 lambda$startMock$3 真正执行
        Field portField = ParallelController.class.getDeclaredField("mockPort");
        portField.setAccessible(true);
        int port = portField.getInt(controller);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        // slow-log 内部 sleep(2000), 设 5s timeout 等结果
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/slow-log?id=u-1"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("logged"));
    }

    @Test
    void stopMockHandlesNullServer() throws Exception {
        // 创建一个 mockServer=null 的实例, 走 stopMock 的 if-false 分支
        ParallelController fresh = new ParallelController(null);
        // 应不抛异常, 走 mockServer==null 分支
        fresh.stopMock();
        Assertions.assertTrue(true);
    }
}