package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UJson;
import lombok.NonNull;



import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;


public interface RenderEngine {

    static RenderEngine build() {
        return new JsonRenderer();
    }

    /**
     * 渲染器协议：解耦 SelfObject 与 JsonRenderer 具体实现，允许第三方实现替换渲染行为。
     * 仅暴露渲染上下文所需的最小 API（递归渲染 + 模板加载器），不包含 withBean/withComponent 等构建器方法。
     */
    interface Renderer {
        Object convertSelf(Object template, SelfObject self) throws RenderException;
        TemplateLoader getTemplateLoader();
        /** 发出 trace 事件。无 consumer 时零开销 (内部检查)。 */
        void trace(TraceEvent event);
    }

    /**
     * 渲染追踪事件: 描述渲染流程中发生的某一动作 (字符串渲染 / 组件调用 / HTTP / 脚本)。
     * 通过 {@link #withTraceConsumer(Consumer)} 注册回调接收事件。
     */
    interface TraceEvent {
        String type();
        String path();
        String message();
        long durationMs();
        Object value();

        /** 人类可读格式: [TYPE] /path  message  (N ms)。 */
        default String format() {
            StringBuilder sb = new StringBuilder();
            sb.append('[').append(type()).append("] ");
            if (path() != null && !path().isEmpty()) sb.append(path()).append(' ');
            sb.append(message() == null ? "" : message());
            if (durationMs() > 0) sb.append("  (").append(durationMs()).append(" ms)");
            return sb.toString();
        }

        /** 便利工厂。 */
        static TraceEvent of(String type, String path, String message, long durationMs, Object value) {
            return new TraceEvent() {
                @Override public String type() { return type; }
                @Override public String path() { return path; }
                @Override public String message() { return message; }
                @Override public long durationMs() { return durationMs; }
                @Override public Object value() { return value; }
                @Override public String toString() { return format(); }
            };
        }
    }

    RenderEngine withComponent(RenderComponent component);

    /**
     * 可反注册的 trace 订阅句柄。流式渲染等临时订阅场景必须在结束时 {@link #close()},
     * 否则消费者会泄漏在共享引擎上, 造成跨请求性能污染。
     */
    interface TraceHandle extends AutoCloseable {
        void remove();
        @Override default void close() { remove(); }
    }

    /**
     * 注册 trace 事件消费者并返回可反注册的句柄。多次调用追加 (多 consumer 都会被通知)。
     * 默认无 consumer, 所有 trace 调用零开销。
     */
    TraceHandle addTraceConsumer(@NonNull Consumer<TraceEvent> consumer);

    /**
     * 注册 trace 事件消费者 (常驻订阅, 兼容旧 API)。
     * 临时订阅请使用 {@link #addTraceConsumer(Consumer)} 并在结束时 close。
     */
    default RenderEngine withTraceConsumer(@NonNull Consumer<TraceEvent> consumer) {
        addTraceConsumer(consumer);
        return this;
    }

    /**
     * 注册输入数据 JSON Schema。渲染前校验 data 是否匹配, 不匹配则抛 RenderException。
     * @param schemaContent JSON 字符串 / Map (已解析 schema) / null (移除校验)
     */
    RenderEngine withInputSchema(Object schemaContent);

    /**
     * 注册输出 (渲染结果) JSON Schema。渲染完成后校验, 不匹配则抛 RenderException。
     * @param schemaContent JSON 字符串 / Map / null
     */
    RenderEngine withOutputSchema(Object schemaContent);

    /**
     * 开启 / 关闭 Groovy 沙箱。开启后模板中的 Groovy 片段 ({@code {= }} / {@code {% %}}) 在受限
     * 环境执行: 循环语句 (while / do-while / for) 编译期拒绝; File / Runtime / System / Class /
     * Eval 等危险调用运行期拦截 (白名单: Func* beans、fastjson2 容器、Map/List、Math、StringUtils)。
     * 违规抛 {@link RenderException}, message 含 "sandbox"。默认关闭, 关闭时与未开启前行为完全一致。
     */
    RenderEngine withSandbox(boolean enabled);

    /**
     * 注入引擎级 {@link ExecutorService}, 供 {@code @class: parallel} 组件并发提交子任务。
     * 注入后该引擎的所有 parallel 任务优先在此 executor 上执行 (便于接入应用统一线程池 / 监控);
     * 同步渲染路径不经过它。executor 按引擎实例隔离, 引擎不管理其生命周期 (shutdown 由调用方负责)。
     * 传 null 恢复内置默认池 (daemon FixedThreadPool, template4j.parallel.poolSize)。
     */
    RenderEngine withExecutor(ExecutorService executor);

    /**
     * 注入引擎级 rest 组件配置 (baseUrl / allowedHosts / maxResponseBytes), 仅影响本引擎的
     * {@code @class: rest} 调用: 实例值优先, 未设置字段回退 RestRenderer 静态默认
     * (系统属性 {@code template4j.rest.*})。传 null 清除, 全部回退静态默认。
     */
    RenderEngine withRestOptions(fun.commons.template4j.core.components.RestOptions options);
    /**
     * 预编译模板句柄: 线程安全, 可多次 render 不同数据。
     * 通过 {@link #compile(Object)} / {@link #compileJson(String)} / {@link #compileYaml(String)} 获取。
     */
    interface Template {
        /**
         * 用给定数据渲染模板。
         * @param data 数据上下文 (Map)
         * @return 渲染结果 (JSONObject / JSONArray / String / Number / Boolean)
         */
        Object render(Map<String, Object> data) throws RenderException;

        /** 便利重载: 接受 JSON/YAML 字符串数据。 */
        default Object render(String data) throws RenderException {
            throw new UnsupportedOperationException("use render(Map) or engine.render() with string data");
        }
    }


    /**
     * <pre>
     *  在引擎加载时，将 bean 注册到转换器中，以便在转换时使用。
     *  例如：converter.withBean("user", user);
     * </pre>
     * @param name bean 名称
     * @param bean bean 实例
     */
    RenderEngine withBean(@NonNull String name, Object bean) throws Exception ;

    RenderEngine withLoader(@NonNull TemplateLoader loader) throws Exception ;


    /**
     * <pre>
     *  在引擎加载时，将 className import guava 脚本中。
     *  例如：converter.withImport("org.apache.commons.lang3.StringUtils");
     *  相当于每个脚本前自动加 ”import org.apache.commons.lang3.StringUtils“
     * </pre>
     * @param className  名称
     *
     */
    RenderEngine withImport(@NonNull String className) throws Exception ;
    /**
     * <pre>
     *  模板+数据>>结果
     *  结果 = 转换 (模板 , 数据 ）
     * </pre>
     * @param template 模板 String,JSONObject,JSONArray, 其它原样返样template
     * @param data 数据 JSONObject
     * @return 转换后的结果
     */
    Object render(Object template, Map<String,Object> data) throws RenderException;

    /**
     * 请求级渲染: 通过 {@link RenderOptions} 为本次渲染单独指定 trace 订阅 / 超时,
     * 不影响引擎全局状态 (trace 订阅在渲染结束后自动移除)。
     * @param template 模板 String,JSONObject,JSONArray, 其它原样返样template
     * @param data 数据 JSONObject
     * @param options 渲染选项 (null 等价于 {@link #render(Object, Map)})
     * @return 转换后的结果
     */
    Object render(Object template, Map<String,Object> data, RenderOptions options) throws RenderException;

    /**
     * 流式渲染: 把 render() 包成流式订阅器,边渲染边吐 trace 事件,最后给结果。
     * 实现原理: 在新线程跑 render(), trace 事件实时进入 subscriber 的 onNext, 完成时 onNext 推一个
     *          包含 "type=RESULT" 的终态事件, 然后 onComplete。失败走 onError。
     *
     * <pre>
     *   RenderEngine.build().renderStream(template, data).subscribe(
     *       ev -> System.out.println(ev.format()),
     *       err -> err.printStackTrace(),
     *       () -> System.out.println("done"));
     * </pre>
     */
    default RenderStream renderStream(Object template, Map<String, Object> data) {
        return new RenderStream(this, template, data);
    }

    /**
     * <pre>
     *  显式预编译模板, 返回线程安全的 {@link Template} 对象, 可多次 render 不同数据。
     *  内部复用 parseStrTemplate 缓存, 热路径比每次 render 快 2-5x。
     * </pre>
     * @param template 模板对象 (String / JSONObject / JSONArray)
     * @return 编译后的 Template 句柄
     */
    Template compile(Object template) throws RenderException;

    Object renderUrl(String resourceUrl , String resourceType , Map<String,Object> data) throws RenderException;

    private  Object parseTemplateJson(String template) throws RenderException{
        try {
            return JSON.parse(template);
        } catch (Exception e) {
            throw new RenderException("/","{template}", "template is not json" , e);
        }
    }

    private  Object parseTemplateYaml(String template) throws RenderException{
        try {
            return UJson.JSONFromYaml(template);
        } catch (IOException e) {
            throw new RenderException("/","{template}", "template is not yaml" , e);
        }
    }

    private JSONObject parseDataString(String data) throws RenderException{
        if (UJson.isJsonObject(data)){
            return  JSONObject.parseObject(data);
        }else{
            Object obj= UJson.JSONFromYamlSafe(data);
            if  (obj instanceof JSONObject jsonObject){
                return jsonObject;
            }else {
                throw new RenderException("/","{data}", "data is not json or yaml object");
            }
        }
    }

    private JSONObject parseDataObject(Object data) throws RenderException{
        try {
            return JSONObject.from(data);
        } catch (Exception e) {
            throw new RenderException("/","{data}", "data cannot cast to json object" , e);
        }
    }

    default RenderEngine withBeans(@NonNull Map<String,Object> beans) throws Exception {
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            withBean(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * <pre>
     *  模板+数据>>结果
     *  结果 = 转换 (模板 , 数据 ）
     * </pre>
     * @param template 模板 String,JSONObject,JSONArray, 其它原样返样template
     * @param data 数据 String, json object 字符串
     * @return 转换后的结果
     */
    default Object render(Object template, String data) throws RenderException{
        return render(template, parseDataString(data));
    }

    default Object render(Object template, Object data) throws RenderException{
        return render(template, parseDataObject(data));
    }

    default Object renderJson(String templateJson, Map<String,Object> data) throws RenderException{
        return  render(parseTemplateJson(templateJson),data);
    }

    default Object renderJson(String templateJson, String data) throws RenderException{
        return  render(parseTemplateJson(templateJson),data);
    }

    default Object renderJson(String templateJson, Object data) throws RenderException{
        return  render(parseTemplateJson(templateJson),data);
    }

    /**
     * 显式编译 JSON 字符串模板, 返回可复用的 {@link Template}。
     */
    default Template compileJson(String templateJson) throws RenderException{
        return compile(parseTemplateJson(templateJson));
    }

    default Object renderYaml(String templateYaml, Map<String,Object> data) throws RenderException{
        return render(parseTemplateYaml(templateYaml), data);
    }
    default Object renderYaml(String templateYaml, String data) throws RenderException{
        return render(parseTemplateYaml(templateYaml), data);
    }
    default Object renderYaml(String templateYaml, Object data) throws RenderException{
        return render(parseTemplateYaml(templateYaml), data);
    }

    /**
     * 显式编译 YAML 字符串模板, 返回可复用的 {@link Template}。
     */
    default Template compileYaml(String templateYaml) throws RenderException{
        return compile(parseTemplateYaml(templateYaml));
    }

    default  Object renderUrl(String resourceUrl , String resourceType, String data) throws RenderException {
        return renderUrl(resourceUrl,resourceType,parseDataString(data));
    }
    default  Object renderUrl(String resourceUrl , String resourceType, Object data) throws RenderException {
        return renderUrl(resourceUrl,resourceType,parseDataObject(data));
    }

     default Object renderJsonUrl(String resourceUrl, Map<String,Object> data) throws RenderException {
        return renderUrl(resourceUrl,"json",data);
    }
    default Object renderJsonUrl(String resourceUrl, String data) throws RenderException {
        return renderUrl(resourceUrl,"json",data);
    }
    default Object renderJsonUrl(String resourceUrl, Object data) throws RenderException {
        return renderUrl(resourceUrl,"json",data);
    }

    default Object renderYamlUrl(String resourceUrl, Map<String,Object> data) throws RenderException {
        return renderUrl(resourceUrl,"yaml",data);
    }
    default Object renderYamlUrl(String resourceUrl, String data) throws RenderException {
        return renderUrl(resourceUrl,"yaml",data);
    }
    default Object renderYamlUrl(String resourceUrl, Object data) throws RenderException {
        return renderUrl(resourceUrl,"yaml",data);
    }

    default Object renderUrl(String resourceUrl, Map<String,Object> data) throws RenderException {
       return renderUrl(resourceUrl,null,data);
    }
    default Object renderUrl(String resourceUrl, String data) throws RenderException {
        return renderUrl(resourceUrl,null,data);
    }
    default Object renderUrl(String resourceUrl, Object data) throws RenderException {
        return renderUrl(resourceUrl,null,data);
    }

}
