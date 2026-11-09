package fun.commons.template4j.core;

import org.apache.commons.lang3.StringUtils;

public class RenderException extends RuntimeException
{

    private static String formatMessage(String path , String name , String message ){
        return "template path:" + path + "/" + StringUtils.defaultIfBlank(name,"") + " " + message ;
    }

    private static String removeUnused(String message){

        if (message == null) return null;

        String[] removes = {"javax.script.ScriptException: ","java.lang.Exception: "};
        String result = message;

        for (String remove : removes) {
            result = StringUtils.removeStart(result,remove);
        }

        if (result.equals(message)){
            return result;
        }else{
            return removeUnused(result);
        }

    }

    public RenderException(String path , String name , String message)
    {
        super(formatMessage(path , name , message));
    }

    public RenderException(String path , String name , String message, Throwable cause)
    {
        super(formatMessage(path , name , message + " : " + removeUnused(cause.getMessage())) , cause);
    }

    @Override
    public String getLocalizedMessage() {
        return super.getLocalizedMessage();
    }

}
