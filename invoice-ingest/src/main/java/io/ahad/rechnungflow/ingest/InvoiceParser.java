package io.ahad.rechnungflow.ingest;
import io.ahad.rechnungflow.core.*;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import org.w3c.dom.*;
import java.util.*;
/** Read-only projection of supported EN16931 UBL/CII business terms; the original remains authoritative. */
public final class InvoiceParser {
    public static final String UBL="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    public static final String CREDIT="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2";
    public static final String CII="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    private static final Map<String,String> NS=Map.of(
        "cac","urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
        "cbc","urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
        "ram","urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100",
        "udt","urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100",
        "rsm",CII);
    public Map<String,Object> parse(byte[] bytes){
        Element root=SafeXml.parse(bytes).getDocumentElement();String ns=root.getNamespaceURI(),name=root.getLocalName();
        if(UBL.equals(ns)&&"Invoice".equals(name))return ubl(root,false);
        if(CREDIT.equals(ns)&&"CreditNote".equals(name))return ubl(root,true);
        if(CII.equals(ns)&&"CrossIndustryInvoice".equals(name))return cii(root);
        throw Problem.of(422,"UNSUPPORTED_SYNTAX");
    }
    private XPath xp(){XPath x=XPathFactory.newInstance().newXPath();x.setNamespaceContext(new NamespaceContext(){
        public String getNamespaceURI(String prefix){return NS.getOrDefault(prefix,"");}
        public String getPrefix(String uri){return NS.entrySet().stream().filter(e->e.getValue().equals(uri)).map(Map.Entry::getKey).findFirst().orElse(null);}
        public Iterator<String> getPrefixes(String uri){return NS.keySet().iterator();}
    });return x;}
    private String text(Node n,String path){
        try{String s=xp().evaluate(path,n).trim();Problem.require(s.length()<=20_000,422,"FIELD_TOO_LARGE");return s;}
        catch(XPathExpressionException e){throw new IllegalStateException(e);}
    }
    private List<Node> nodes(Node n,String path){
        try{NodeList list=(NodeList)xp().evaluate(path,n,XPathConstants.NODESET);Problem.require(list.getLength()<=1_000,422,"TOO_MANY_LINES");
            List<Node> result=new ArrayList<>();for(int i=0;i<list.getLength();i++)result.add(list.item(i));return result;
        }catch(XPathExpressionException e){throw new IllegalStateException(e);}
    }
    private String fallback(String a,String b){return a.isBlank()?b:a;}
    private String zero(String s){return s.isBlank()?"0":s;}
    private Map<String,Object> ublParty(Node root,String path){
        String base=path+"/cac:Party/";
        return Values.map("name",fallback(text(root,base+"cac:PartyLegalEntity/cbc:RegistrationName"),text(root,base+"cac:PartyName/cbc:Name")),
            "taxId",text(root,base+"cac:PartyTaxScheme[cac:TaxScheme/cbc:ID='VAT']/cbc:CompanyID"),
            "registration",text(root,base+"cac:PartyLegalEntity/cbc:CompanyID"),"endpoint",text(root,base+"cbc:EndpointID"),
            "street",text(root,base+"cac:PostalAddress/cbc:StreetName"),"city",text(root,base+"cac:PostalAddress/cbc:CityName"),
            "postalCode",text(root,base+"cac:PostalAddress/cbc:PostalZone"),"country",text(root,base+"cac:PostalAddress/cac:Country/cbc:IdentificationCode"));
    }
    private Map<String,Object> ubl(Element root,boolean credit){
        var m=Values.map("syntax",credit?"UBL_CREDIT_NOTE":"UBL_INVOICE","profile",text(root,"cbc:CustomizationID"),
            "number",text(root,"cbc:ID"),"issueDate",text(root,"cbc:IssueDate"),"dueDate",text(root,"cbc:DueDate"),
            "type",text(root,credit?"cbc:CreditNoteTypeCode":"cbc:InvoiceTypeCode"),"credit",credit,"currency",text(root,"cbc:DocumentCurrencyCode"),
            "buyerReference",text(root,"cbc:BuyerReference"),"purchaseOrder",text(root,"cac:OrderReference/cbc:ID"),
            "seller",ublParty(root,"cac:AccountingSupplierParty"),"buyer",ublParty(root,"cac:AccountingCustomerParty"),
            "iban",text(root,"cac:PaymentMeans/cac:PayeeFinancialAccount/cbc:ID").replace(" ",""),"paymentReference",text(root,"cac:PaymentMeans/cbc:PaymentID"),
            "paymentTerms",text(root,"cac:PaymentTerms/cbc:Note"),"note",text(root,"cbc:Note"));
        String total="cac:LegalMonetaryTotal/";String currency=Values.text(m,"currency");
        String tax="";for(Node t:nodes(root,"cac:TaxTotal")){String c=text(t,"cbc:TaxAmount/@currencyID");if(c.equals(currency)||c.isEmpty()){tax=text(t,"cbc:TaxAmount");break;}}
        m.put("totals",Values.map("lineNet",text(root,total+"cbc:LineExtensionAmount"),"net",text(root,total+"cbc:TaxExclusiveAmount"),"tax",tax,
            "gross",text(root,total+"cbc:TaxInclusiveAmount"),"allowances",zero(text(root,total+"cbc:AllowanceTotalAmount")),"charges",zero(text(root,total+"cbc:ChargeTotalAmount")),
            "prepaid",zero(text(root,total+"cbc:PrepaidAmount")),"rounding",zero(text(root,total+"cbc:PayableRoundingAmount")),"payable",text(root,total+"cbc:PayableAmount")));
        List<Object> lines=new ArrayList<>();for(Node n:nodes(root,credit?"cac:CreditNoteLine":"cac:InvoiceLine")){
            lines.add(Values.map("id",text(n,"cbc:ID"),"description",fallback(text(n,"cac:Item/cbc:Name"),text(n,"cac:Item/cbc:Description")),
                "quantity",text(n,credit?"cbc:CreditedQuantity":"cbc:InvoicedQuantity"),"unit",text(n,(credit?"cbc:CreditedQuantity":"cbc:InvoicedQuantity")+"/@unitCode"),
                "net",text(n,"cbc:LineExtensionAmount"),"unitPrice",text(n,"cac:Price/cbc:PriceAmount"),"vatCategory",text(n,"cac:Item/cac:ClassifiedTaxCategory/cbc:ID"),
                "vatRate",text(n,"cac:Item/cac:ClassifiedTaxCategory/cbc:Percent")));
        }m.put("lines",lines);
        List<Object> taxes=new ArrayList<>();for(Node n:nodes(root,"cac:TaxTotal/cac:TaxSubtotal")){
            String c=text(n,"cbc:TaxAmount/@currencyID");if(!c.isBlank()&&!c.equals(currency))continue;
            taxes.add(Values.map("category",text(n,"cac:TaxCategory/cbc:ID"),"rate",text(n,"cac:TaxCategory/cbc:Percent"),"basis",text(n,"cbc:TaxableAmount"),"amount",text(n,"cbc:TaxAmount")));
        }m.put("taxes",taxes);return m;
    }
    private Map<String,Object> ciiParty(Node root,String path){
        return Values.map("name",text(root,path+"/ram:Name"),"taxId",text(root,path+"/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']"),
            "registration",text(root,path+"/ram:SpecifiedLegalOrganization/ram:ID"),"endpoint",text(root,path+"/ram:URIUniversalCommunication/ram:URIID"),
            "street",text(root,path+"/ram:PostalTradeAddress/ram:LineOne"),"city",text(root,path+"/ram:PostalTradeAddress/ram:CityName"),
            "postalCode",text(root,path+"/ram:PostalTradeAddress/ram:PostcodeCode"),"country",text(root,path+"/ram:PostalTradeAddress/ram:CountryID"));
    }
    private String ciiDate(String s){return s.matches("[0-9]{8}")?s.substring(0,4)+"-"+s.substring(4,6)+"-"+s.substring(6):s;}
    private Map<String,Object> cii(Element root){
        String tx="rsm:SupplyChainTradeTransaction/",a=tx+"ram:ApplicableHeaderTradeAgreement",s=tx+"ram:ApplicableHeaderTradeSettlement",tot=s+"/ram:SpecifiedTradeSettlementHeaderMonetarySummation/";
        String type=text(root,"rsm:ExchangedDocument/ram:TypeCode");
        var m=Values.map("syntax","CII","profile",text(root,"rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID"),
            "number",text(root,"rsm:ExchangedDocument/ram:ID"),"issueDate",ciiDate(text(root,"rsm:ExchangedDocument/ram:IssueDateTime/udt:DateTimeString")),
            "dueDate",ciiDate(text(root,s+"/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/udt:DateTimeString")),"type",type,"credit",type.equals("381"),
            "currency",text(root,s+"/ram:InvoiceCurrencyCode"),"buyerReference",text(root,a+"/ram:BuyerReference"),"purchaseOrder",text(root,a+"/ram:BuyerOrderReferencedDocument/ram:IssuerAssignedID"),
            "seller",ciiParty(root,a+"/ram:SellerTradeParty"),"buyer",ciiParty(root,a+"/ram:BuyerTradeParty"),
            "iban",text(root,s+"/ram:SpecifiedTradeSettlementPaymentMeans/ram:PayeePartyCreditorFinancialAccount/ram:IBANID").replace(" ",""),
            "paymentReference",text(root,s+"/ram:PaymentReference"),"paymentTerms",text(root,s+"/ram:SpecifiedTradePaymentTerms/ram:Description"),
            "note",text(root,"rsm:ExchangedDocument/ram:IncludedNote/ram:Content"));
        String tax="",currency=Values.text(m,"currency");for(Node n:nodes(root,tot+"ram:TaxTotalAmount")){String c=text(n,"@currencyID");if(c.equals(currency)||c.isBlank()){tax=n.getTextContent().trim();break;}}
        m.put("totals",Values.map("lineNet",text(root,tot+"ram:LineTotalAmount"),"net",text(root,tot+"ram:TaxBasisTotalAmount"),"tax",tax,"gross",text(root,tot+"ram:GrandTotalAmount"),
            "allowances",zero(text(root,tot+"ram:AllowanceTotalAmount")),"charges",zero(text(root,tot+"ram:ChargeTotalAmount")),"prepaid",zero(text(root,tot+"ram:TotalPrepaidAmount")),
            "rounding",zero(text(root,tot+"ram:RoundingAmount")),"payable",text(root,tot+"ram:DuePayableAmount")));
        List<Object> lines=new ArrayList<>();for(Node n:nodes(root,tx+"ram:IncludedSupplyChainTradeLineItem")){
            lines.add(Values.map("id",text(n,"ram:AssociatedDocumentLineDocument/ram:LineID"),"description",text(n,"ram:SpecifiedTradeProduct/ram:Name"),
                "quantity",text(n,"ram:SpecifiedLineTradeDelivery/ram:BilledQuantity"),"unit",text(n,"ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode"),
                "unitPrice",text(n,"ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount"),
                "net",text(n,"ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount"),
                "vatCategory",text(n,"ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:CategoryCode"),"vatRate",text(n,"ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:RateApplicablePercent")));
        }m.put("lines",lines);
        List<Object> taxes=new ArrayList<>();for(Node n:nodes(root,s+"/ram:ApplicableTradeTax"))taxes.add(Values.map("category",text(n,"ram:CategoryCode"),"rate",text(n,"ram:RateApplicablePercent"),"basis",text(n,"ram:BasisAmount"),"amount",text(n,"ram:CalculatedAmount")));
        m.put("taxes",taxes);return m;
    }
}
