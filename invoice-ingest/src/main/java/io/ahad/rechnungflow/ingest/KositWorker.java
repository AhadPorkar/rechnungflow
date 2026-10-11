package io.ahad.rechnungflow.ingest;
import io.ahad.rechnungflow.core.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
/** Internal subprocess entrypoint. stdout is not trusted; result is an explicit local output file. */
public final class KositWorker {
    public static void main(String[] args)throws Exception{
        Path xml=Path.of(args[0]),scenarios=Path.of(args[1]),out=Path.of(args[2]);
        Class<?> cfg=Class.forName("de.kosit.validationtool.api.Configuration"),processor=Class.forName("net.sf.saxon.s9api.Processor");
        Object builder=cfg.getMethod("load",java.net.URI.class).invoke(null,scenarios.toUri());
        Object proc=Class.forName("de.kosit.validationtool.impl.xml.ProcessorProvider").getMethod("getProcessor").invoke(null);
        Object configuration=builder.getClass().getMethod("build",processor).invoke(builder,proc);
        Object check=Class.forName("de.kosit.validationtool.impl.DefaultCheck").getConstructor(cfg).newInstance(configuration);
        Object input=Class.forName("de.kosit.validationtool.api.InputFactory").getMethod("read",Path.class).invoke(null,xml);
        Class<?> inputType=Class.forName("de.kosit.validationtool.api.Input");
        Object result=check.getClass().getMethod("checkInput",inputType).invoke(check,input);
        Class<?> resultType=Class.forName("de.kosit.validationtool.api.Result");
        boolean processed=(Boolean)resultType.getMethod("isProcessingSuccessful").invoke(result);
        String recommendation=String.valueOf(resultType.getMethod("getAcceptRecommendation").invoke(result));
        String status=processed&&recommendation.equals("ACCEPTABLE")?"PASS":processed&&recommendation.equals("REJECT")?"FAIL":"ERROR";
        StringWriter report=new StringWriter();Object document=resultType.getMethod("getReportDocument").invoke(result);
        if(document!=null){TransformerFactory tf=TransformerFactory.newDefaultInstance();tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");
            tf.newTransformer().transform(new DOMSource((org.w3c.dom.Document)document),new StreamResult(report));}
        var answer=Values.map("status",status,"processed",processed,"recommendation",recommendation,"report",report.toString());
        Files.writeString(out,Json.stringify(answer));
    }
}
