package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UJson;
import lombok.NonNull;



import java.io.IOException;
import java.util.Map;
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
     * 注册 trace 事件消费者。多次调用追加 (多 consumer 都会被通知)。
     * 默认无 consumer, 所有 trace 调用零开销。
     */
    RenderEngine withTraceConsumer(@NonNull Consumer<TraceEvent> consumer);

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
