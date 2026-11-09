package fun.commons.template4j.core;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SelfObject {

    public final List<String> locks;

    public final RenderEngine.Renderer renderer;

    public final SelfObject parent;

    @Getter
    final Map<String,Object> varMap = new LinkedHashMap<>();

    @Getter
    @Setter
    protected  Map<String,Object> data;

    @Getter
    @Setter
    public boolean parentVars = true;

    public String name = "";

    @Setter
    public String resourceUrl;

    private final LockManager lockManager;

    public SelfObject(RenderEngine.Renderer renderer, Map<String,Object> data) {
        this.renderer = renderer;
        this.data = data;
        this.parent = null;
        this.name = "";
        this.lockManager = new LockManager();
        this.locks = lockManagerBackingList();
    }

    public SelfObject(SelfObject parent,String name) {
        this.renderer = parent.renderer;
        this.data = parent.data;
        this.parent = parent;
        this.name = name;
        this.lockManager = parent.lockManager.shareWithChild();
        this.locks = lockManagerBackingList();
    }

    private List<String> lockManagerBackingList() {
        // 保留对外可见的 List<String> locks API（与历史测试反射访问兼容）
        return lockManager.snapshot();
    }

    public String getResourceUrl() {
        if (StringUtils.isNotBlank(resourceUrl) || null == parent){
            return this.resourceUrl;
        } else {
            return parent.getResourceUrl();
        }
    }

    public String path() {
        return PathTracker.build(this);
    }

    public SelfObject getRoot() {
        return PathTracker.rootOf(this);
    }


    //取变量值,递归到上级
    public Object vars(String name) throws RenderException {

        Object result = getVar(name);

        if (result == null && null != parent && parentVars) {
            result = parent.vars(name);
        }

        //如果结果是函数,则设置上下文为调用方法的上下文
        if  (result instanceof FuncObject) {
             ((FuncObject)result).setSelf(this);
        }

        return result;

    }


    //取变量值,不递归到上级
    public Object getVar(String name) throws RenderException {

        String varKey = "$" + name;
        String funcKey = "$" + name + "()";

        Object result = null;

        if (varMap.containsKey(name)) {

            result = varMap.get(name);

        }else if (varMap.containsKey(varKey)) {

            Object v = varMap.get(varKey);
            //先移出，再渲染，后再放回。这样可以避免循环引用。
            varMap.remove(varKey);
            varMap.put(name, convertSelf(v,  varKey));
            result = varMap.get(name);

        }else if (varMap.containsKey(funcKey)) {
            // 每次都创建一个新对象，每次都渲染，每次的self环境都不一样。
            result = new FuncObject(funcKey, varMap.get(funcKey));
        }

        return result;

    }


    public Object convertSelf(Object template, String name) throws RenderException {
       return renderer.convertSelf(template, new SelfObject(this, name));
    }

    public Object convertSelf(Object template) throws RenderException {
        return renderer.convertSelf(template, this);
    }

    public void removeVar(String k) throws RenderException {
        String name = k;
        name = StringUtils.removeStart(name, "$");
        name = StringUtils.removeEnd(name, "()");
        varMap.remove(name);
        varMap.remove("$" + name);
        varMap.remove("$" + name + "()");
    }

    public void putVar(String k, Object v) throws RenderException {
        removeVar(k);
        varMap.put(k,v);
    }


    public void putVarDefine(Object v) throws RenderException {
        if (v instanceof Map vMap) {
            for (Object obj : vMap.keySet()) {
                String k = String.valueOf(obj);
                putVar(k.startsWith("$") ? k : "$" + k, vMap.get(k));
            }
        }
        else if (v instanceof List vList){
            for (Object vv : vList) {
                putVarDefine(vv);
            }
        }else {
            throw new RenderException(this.path(), this.name + "/$", "$ vars type is not support :" + v.getClass().getName());
        }
    }

    public void putVars(Object v) throws RenderException {

        if (v == null){
            return;
        }

        if (v instanceof Map vMap) {
            for (Object obj : vMap.keySet()) {
                String k = String.valueOf(obj);
                putVar(k, vMap.get(k));
            }
        }
        else if (v instanceof List vList){
            for (Object vv : vList) {
                putVars(vv);
            }
        }else {
            throw new RenderException(this.path(), this.name + "/$", "$ vars type is not support :" + v.getClass().getName());
        }
    }

    public boolean lock(String name) {
        return lockManager.acquire(name);
    }
    public boolean unLock(String name){
        return lockManager.release(name);
    }

}
