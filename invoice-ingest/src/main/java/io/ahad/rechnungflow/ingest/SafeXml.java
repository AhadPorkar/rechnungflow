package io.ahad.rechnungflow.ingest;
import io.ahad.rechnungflow.core.*;
import javax.xml.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import java.io.*;
/** No DTDs, external schemas, XInclude, external entities, or unbounded tree depth. */
public final class SafeXml {
    public static final int MAX_BYTES=2_097_152;
    private SafeXml(){}
    public static Document parse(byte[] bytes){return parse(bytes,MAX_BYTES);}
    public static Document parse(byte[] bytes,int maximum){
        Problem.require(bytes!=null&&bytes.length>0&&bytes.length<=maximum,413,"FILE_TOO_LARGE");
        try{
            SAXParserFactory sf=SAXParserFactory.newInstance();sf.setNamespaceAware(true);
            sf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            sf.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            sf.setFeature("http://xml.org/sax/features/external-general-entities",false);
            sf.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            sf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            var sax=sf.newSAXParser();sax.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");sax.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            sax.parse(new ByteArrayInputStream(bytes),new DefaultHandler(){
                int depth,nodes;
                @Override public void startElement(String uri,String local,String q,Attributes a)throws SAXException{
                    if(++depth>64||++nodes>30_000||a.getLength()>64)throw new SAXException("limit");
                    if("http://www.w3.org/2001/XInclude".equals(uri))throw new SAXException("xinclude");
                }
                @Override public void endElement(String u,String l,String q){depth--;}
                @Override public void error(SAXParseException e)throws SAXException{throw e;}
                @Override public void fatalError(SAXParseException e)throws SAXException{throw e;}
            });
            DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setNamespaceAware(true);f.setXIncludeAware(false);f.setExpandEntityReferences(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            f.setFeature("http://xml.org/sax/features/external-general-entities",false);f.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            DocumentBuilder d=f.newDocumentBuilder();d.setEntityResolver((publicId,systemId)->{throw new SAXException("external entity");});
            d.setErrorHandler(new DefaultHandler(){@Override public void fatalError(SAXParseException e)throws SAXException{throw e;}});
            return d.parse(new ByteArrayInputStream(bytes));
        }catch(Exception e){throw Problem.of(422,"UNSAFE_OR_INVALID_XML");}
    }
}
