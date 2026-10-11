package io.ahad.rechnungflow.testing;
import io.ahad.rechnungflow.core.*;import io.ahad.rechnungflow.ingest.*;
import java.util.*;import java.math.*;import java.nio.charset.StandardCharsets;
import static io.ahad.rechnungflow.testing.TestKit.*;
public final class IngestTest {
    static final InvoiceParser parser=new InvoiceParser();static final BasicRules rules=new BasicRules();
    static Map<String,Object> good()throws Exception{return parser.parse(Fixtures.xml("01-ubl-office.xml"));}
    static boolean issue(Map<String,Object>d,String code){return rules.check(d).stream().map(Json::object).anyMatch(x->Values.text(x,"code").equals(code));}
    public static void main(String[]args)throws Exception{
        TestKit t=new TestKit("ingest");
        t.test("UBL namespace parsing",()->eq("UBL_INVOICE",Values.text(good(),"syntax")));
        t.test("UBL invoice number",()->eq("NW-2026-1001",Values.text(good(),"number")));
        t.test("UBL tax identifier",()->eq("DE123456789",Values.text(Json.map(good(),"seller"),"taxId")));
        t.test("UBL exact payable",()->eq("1190.00",Values.text(Json.map(good(),"totals"),"payable")));
        t.test("UBL seller IBAN",()->eq("DE79000000001234567890",Values.text(good(),"iban")));
        t.test("UBL items available",()->yes(!Json.list(good(),"lines").isEmpty()));
        t.test("CII namespace parsing",()->eq("CII",Values.text(parser.parse(Fixtures.xml("07-cii-consulting.xml")),"syntax")));
        t.test("CII amounts",()->eq("2856.00",Values.text(Json.map(parser.parse(Fixtures.xml("07-cii-consulting.xml")),"totals"),"payable")));
        t.test("CII seller identity",()->eq("DE111222333",Values.text(Json.map(parser.parse(Fixtures.xml("07-cii-consulting.xml")),"seller"),"taxId")));
        t.test("Credit note distinguished",()->yes(Json.bool(parser.parse(Fixtures.xml("08-ubl-credit-note.xml")),"credit",false)));
        for(String file:List.of("01-ubl-office.xml","02-ubl-machinery.xml","03-ubl-approved.xml","04-ubl-export.xml","06-ubl-bank-change.xml","07-cii-consulting.xml","08-ubl-credit-note.xml","09-ubl-corrected.xml"))
            t.test("Synthetic fixture plausibility "+file,()->eq(List.of(),rules.check(parser.parse(Fixtures.xml(file)))));
        t.test("Bad totals detected",()->yes(issue(parser.parse(Fixtures.xml("05-ubl-bad-total.xml")),"TOTAL_MISMATCH")));
        t.test("Forged root namespace rejected",()->CoreTest.reject(()->parser.parse(Fixtures.xml("12-fake-root.xml"))));
        t.test("DTD and file entities rejected",()->CoreTest.reject(()->parser.parse(Fixtures.xml("11-xxe.xml"))));
        t.test("HTTP entity rejected",()->CoreTest.reject(()->SafeXml.parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'http://127.0.0.1:1/'>]><x>&e;</x>".getBytes())));
        t.test("Entity expansion rejected",()->CoreTest.reject(()->SafeXml.parse("<!DOCTYPE x [<!ENTITY a 'xx'><!ENTITY b '&a;&a;'>]><x>&b;</x>".getBytes())));
        t.test("Malformed XML rejected",()->CoreTest.reject(()->SafeXml.parse("<a>".getBytes())));
        t.test("Excessive XML depth rejected",()->CoreTest.reject(()->SafeXml.parse(("<x>".repeat(70)+"</x>".repeat(70)).getBytes())));
        t.test("Excessive XML nodes rejected",()->CoreTest.reject(()->SafeXml.parse(("<x>"+"<i/>".repeat(30_005)+"</x>").getBytes())));
        t.test("XML over upload bound rejected",()->CoreTest.reject(()->SafeXml.parse(new byte[2_097_153])));
        t.test("XInclude is never fetched",()->{error("UNSAFE_OR_INVALID_XML",()->SafeXml.parse("<x xmlns:xi='http://www.w3.org/2001/XInclude'><xi:include href='file:///etc/passwd'/></x>".getBytes()));});
        t.test("Missing invoice number detected",()->{var d=good();d.put("number","");yes(issue(d,"REQUIRED_VALUE"));});
        t.test("Missing seller name detected",()->{var d=good();Json.map(d,"seller").put("name","");yes(issue(d,"REQUIRED_VALUE"));});
        t.test("Missing buyer name detected",()->{var d=good();Json.map(d,"buyer").put("name","");yes(issue(d,"REQUIRED_VALUE"));});
        for(String date:List.of("2026-02-30","2026-13-01","0000-01-01","2026-1-01","2026-01-01T00:00:00Z"))
            t.test("Invalid issue date "+date,()->{var d=good();d.put("issueDate",date);yes(issue(d,"INVALID_DATE"));});
        t.test("Unknown currency detected",()->{var d=good();d.put("currency","ZZZ");yes(issue(d,"UNKNOWN_CURRENCY"));});
        t.test("Unsupported invoice type",()->{var d=good();d.put("type","999");yes(issue(d,"UNSUPPORTED_TYPE"));});
        t.test("Credit type against invoice root denied",()->{var d=good();d.put("type","381");yes(issue(d,"TYPE_ROOT_MISMATCH"));});
        for(String profile:List.of("urn:minimum","urn:basicwl","urn:basic-wl"))
            t.test("Insufficient profile "+profile,()->{var d=good();d.put("profile",profile);yes(issue(d,"INSUFFICIENT_PROFILE"));});
        t.test("Fractional cents rejected",()->{var d=good();Json.map(d,"totals").put("net","1000.001");yes(issue(d,"AMOUNT_PRECISION"));});
        t.test("Total exponent denied",()->{var d=good();Json.map(d,"totals").put("net","1e3");yes(issue(d,"AMOUNT_FORMAT"));});
        t.test("Payable reconciliation detects mismatch",()->{var d=good();Json.map(d,"totals").put("payable","1191.00");yes(issue(d,"TOTAL_MISMATCH"));});
        t.test("Allowance reconciliation detects mismatch",()->{var d=good();Json.map(d,"totals").put("allowances","10.00");yes(issue(d,"TOTAL_MISMATCH"));});
        t.test("Prepayment reconciliation accepted",()->{var d=good();Json.map(d,"totals").put("prepaid","90.00");Json.map(d,"totals").put("payable","1100.00");eq(List.of(),rules.check(d));});
        t.test("Missing line detected",()->{var d=good();d.put("lines",List.of());yes(issue(d,"LINES_MISSING"));});
        t.test("Duplicate line ID detected",()->{var d=good();var line=Json.list(d,"lines").getFirst();d.put("lines",List.of(line,line));yes(issue(d,"LINE_ID_INVALID"));});
        t.test("Missing line description",()->{var d=good();Json.object(Json.list(d,"lines").getFirst()).put("description","");yes(issue(d,"REQUIRED_VALUE"));});
        t.test("Line sum reconciliation",()->{var d=good();Json.object(Json.list(d,"lines").getFirst()).put("net","999.00");yes(issue(d,"LINE_SUM_MISMATCH"));});
        t.test("Tax sum reconciliation",()->{var d=good();Json.object(Json.list(d,"taxes").getFirst()).put("amount","189.00");yes(issue(d,"TAX_SUM_MISMATCH"));});
        t.test("Quantity numeric validation",()->{var d=good();Json.object(Json.list(d,"lines").getFirst()).put("quantity","NaN");yes(issue(d,"AMOUNT_FORMAT"));});
        t.test("Valid German IBAN",()->yes(BasicRules.ibanValid("DE79000000001234567890")));
        t.test("Spaced lowercase IBAN normalized",()->yes(BasicRules.ibanValid("de79 0000 0000 1234 5678 90")));
        t.test("Invalid IBAN checksum",()->yes(!BasicRules.ibanValid("DE78000000001234567890")));
        t.test("Invalid IBAN country length",()->yes(!BasicRules.ibanValid("DE7900000000123456789")));
        t.test("Invalid IBAN symbols",()->yes(!BasicRules.ibanValid("DE79<script>")));
        t.test("XML decimal permits leading zeros",()->eq(new BigDecimal("12.50"),BasicRules.amount("0012.50")));
        t.test("XML decimal permits explicit positive",()->eq(new BigDecimal("1.00"),BasicRules.amount("+1.00")));
        t.test("XML decimal permits fractional form",()->eq(new BigDecimal("0.5"),BasicRules.amount(".5")));
        for(String invalid:List.of("1e3","NaN","Infinity","1,00","1.123456789","1234567890123456"))t.test("Decimal boundary rejects "+invalid,()->CoreTest.reject(()->BasicRules.amount(invalid)));
        t.test("Unconfigured official validation fails closed",()->eq("UNAVAILABLE",Values.text(ValidationEngine.unavailable().validate(Fixtures.xml("01-ubl-office.xml")),"status")));
        t.test("Unconfigured validator not reported installed",()->yes(!Json.bool(ValidationEngine.unavailable().capabilities(),"configured",true)));
        t.finish("build/test-results/ingest.xml");
    }
}
