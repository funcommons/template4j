package fun.commons.template4j.core;

import fun.commons.template4j.utils.UThrow;
import lombok.Getter;
import lombok.Setter;


import java.util.HashMap;
import java.util.Map;


public interface RenderComponent {
    class RenderContext {

        @Setter @Getter
        public String name;

        @Setter @Getter
        public Map<String,Object> map;

        @Setter @Getter
        public SelfObject self;

        @Setter @Getter
        public RenderEngine.Renderer renderer;

        public boolean containsField(String fieldName){
            return map.containsKey(fieldName);
        }

        public String firstNonField(String... fields){
            for (String field : fields) {
                if(map.containsKey(field)) {
                    return field;
                }
            }
            return null;
        }




        public <T> T render(Object template) throws Exception {
            return render(template, new HashMap<>());
        }

        public <T> T render(Object template, String varName, Object varObject) throws Exception {
            return render(template, singleVarMap(varName, varObject));
        }

        public <T> T render(Object template, String varName1, Object varObject1, String varName2, Object varObject2) throws Exception {
            return render(template, doubleVarMap(varName1, varObject1, varName2, varObject2));
        }

        public <T> T render(Object template, Map<String, Object> subMap) throws Exception {
            SelfObject subSelf = new SelfObject(self, name);
            subSelf.putVars(subMap);
            return (T) subSelf.convertSelf(template);
        }


        public <T> T renderField(String fieldName, Map<String, Object> subMap) throws Exception {
            SelfObject subSelf = new SelfObject(self, fieldName);
            subSelf.putVars(subMap);
            return (T) subSelf.convertSelf(map.get(fieldName));
        }


        public <T> T renderField(String fieldName) throws Exception {
            return renderField(fieldName, new HashMap<>());
        }

        public <T> T renderField(String fieldName, String varName, Object varObject) throws Exception {
            return renderField(fieldName, singleVarMap(varName, varObject));
        }

        public <T> T renderField(String fieldName, String varName1, Object varObject1, String varName2, Object varObject2) throws Exception {
            return renderField(fieldName, doubleVarMap(varName1, varObject1, varName2, varObject2));
        }

        private static Map<String, Object> singleVarMap(String varName, Object varObject) {
            Map<String, Object> m = new HashMap<>();
            m.put(varName, varObject);
            return m;
        }

        private static Map<String, Object> doubleVarMap(String varName1, Object varObject1, String varName2, Object varObject2) {
            Map<String, Object> m = new HashMap<>();
            m.put(varName1, varObject1);
            m.put(varName2, varObject2);
            return m;
        }

        public void assertFieldOne(String... names) throws Exception {
            boolean test = false;
            for  (String fieldName : names) {
                    if (map.containsKey(fieldName)){
                        test = true;
                        break;
                    }
            }
            UThrow.assertArgument(test,name + " component one of [" + String.join(",", names) + "] field must exist.");
        }

        public void assertField(String... names) throws Exception{
            for  (String fieldName : names) {
                UThrow.assertArgument(map.containsKey(fieldName),name +" component ["+ fieldName + "] field must exist.");
            }
        }

        public <T> T getValue(String fieldName) {
            return (T)map.get(fieldName);
        }

        public <T> T getValue(String fieldName,T defaultValue) {
            if ( fieldName != null && map.containsKey(fieldName)) {
                return (T) map.getOrDefault(fieldName, defaultValue);
            }else {
                return defaultValue;
            }
        }

    }



    // 初始化组件
    default Object doRender(Map<String,Object> map, SelfObject self) throws RenderException {
        try {
            RenderContext context = new RenderContext();
            context.setMap(map);
            context.setSelf(self);
            context.setName(name());
            context.setRenderer(self.renderer);
            return render(context);
        }catch (Exception e){
            throw new RenderException(self.path(),"@class:" + name(),"component render error",e);
        }

    }

    // 渲染组件
    Object render(RenderContext context) throws Exception;
    String name();

}
