package fun.commons.template4j.core.beans;


import fun.commons.template4j.core.LoaderUtil;
import fun.commons.template4j.core.SelfObject;
import fun.commons.template4j.utils.UString;

import java.io.IOException;

public class FuncLoad {

    private final SelfObject self;

    public FuncLoad(SelfObject self) {
        this.self = self;
    }

    private Object loadResource(String url) throws IOException {

        String rootUrl = self.getResourceUrl();
        String resourceUrl = UString.relativeUrl(rootUrl, url);
        return LoaderUtil.loadResource(self.renderer.getTemplateLoader(), resourceUrl, null);

    }

    public Object call(String url) throws IOException {
        return loadResource(url);
    }

}