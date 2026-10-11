package io.ahad.rechnungflow.ingest;
import io.ahad.rechnungflow.core.*;
import org.w3c.dom.*;
import java.util.*;
/** Bounded SVRL diagnostics for display only. Acceptance comes exclusively from the KoSIT Result API. */
public final class ReportDiagnostics {
    private ReportDiagnostics(){}
    public static List<Object> extract(Document report){
        List<Object> out=new ArrayList<>();
        for(String type:List.of("failed-assert","successful-report")){
            NodeList nodes=report.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl",type);
            for(int i=0;i<nodes.getLength()&&out.size()<100;i++){
                Element node=(Element)nodes.item(i);String severity=node.getAttribute("flag");if(severity.isBlank())severity=node.getAttribute("role");
                if(severity.isBlank())severity=type.equals("failed-assert")?"error":"warning";
                NodeList text=node.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl","text");String message=text.getLength()==0?"":text.item(0).getTextContent();
                out.add(Values.map("rule",bound(node.getAttribute("id"),200),"severity",bound(severity.toUpperCase(Locale.ROOT),40),"location",bound(node.getAttribute("location"),1000),"message",bound(message,2000)));
            }
        }
        return out;
    }
    private static String bound(String s,int max){s=s.trim();return s.substring(0,Math.min(max,s.length()));}
}
