package io.ahad.rechnungflow.testing;
import io.ahad.rechnungflow.core.*;import io.ahad.rechnungflow.ingest.*;
import java.util.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.zip.*;import java.io.*;
import static io.ahad.rechnungflow.testing.TestKit.*;
/** Official-tool behavior is NOT claimed: manifest fixtures and engine doubles are synthetic. */
public final class ValidationTest {
 static final String NS="http://purl.oclc.org/dsdl/svrl";
 static List<Object> diagnostics(String inner){return ReportDiagnostics.extract(SafeXml.parse(("<r xmlns:s='"+NS+"'>"+inner+"</r>").getBytes(StandardCharsets.UTF_8)));}
 static Map<String,Object> diagnostic(String inner){return Json.object(diagnostics(inner).getFirst());}
 static Map<String,Object> validation(Fixtures f,String id){return Json.map(f.get(id),"validation");}
 static void manifestTest(TestKit t,String label,java.util.function.BiConsumer<Path,Map<String,Object>> change){t.test(label,()->{Path d=Files.createTempDirectory("rf-pin-");try{Files.createDirectories(d.resolve("config"));byte[] jar="SYNTHETIC-NOT-A-JAR".getBytes();Files.write(d.resolve("validator-1.6.3-standalone.jar"),jar);Files.writeString(d.resolve("config/scenarios.xml"),"<synthetic/>");var m=Values.map("version","1.6.3","configuration","2026-08-31","jarSha256",Crypto.sha(jar),"configurationFiles",Values.map("scenarios.xml",Crypto.sha("<synthetic/>")));change.accept(d,m);Files.writeString(d.resolve("manifest.json"),Json.stringify(m));error("VALIDATOR_PIN_MISMATCH",()->new KositEngine(d,d.resolve("work"),2));}finally{try(var walk=Files.walk(d)){for(Path x:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(x);}}});}
 public static void main(String[]args)throws Exception{TestKit t=new TestKit("validation");
  t.test("Empty report has no fabricated diagnostics",()->eq(List.of(),diagnostics("")));
  t.test("Failed assertion defaults to error",()->eq("ERROR",Values.text(diagnostic("<s:failed-assert id='BR-01'><s:text>Missing value</s:text></s:failed-assert>"),"severity")));
  t.test("Successful report defaults to warning",()->eq("WARNING",Values.text(diagnostic("<s:successful-report><s:text>Check</s:text></s:successful-report>"),"severity")));
  t.test("Explicit severity retained",()->eq("FATAL",Values.text(diagnostic("<s:failed-assert flag='fatal'/>"),"severity")));
  t.test("Role used when flag absent",()->eq("INFO",Values.text(diagnostic("<s:successful-report role='info'/>"),"severity")));
  t.test("Wrong namespace cannot impersonate SVRL",()->eq(List.of(),diagnostics("<failed-assert><text>Fake</text></failed-assert>")));
  t.test("Rule identifier retained",()->eq("BR-DE-01",Values.text(diagnostic("<s:failed-assert id='BR-DE-01'/>"),"rule")));
  t.test("XPath retained for diagnostics",()->eq("/Invoice",Values.text(diagnostic("<s:failed-assert location='/Invoice'/>"),"location")));
  t.test("Nested diagnostic text extracted",()->eq("Missing number",Values.text(diagnostic("<s:failed-assert><s:text>Missing <b>number</b></s:text></s:failed-assert>"),"message")));
  t.test("Diagnostic message bounded",()->eq(2000,Values.text(diagnostic("<s:failed-assert><s:text>"+"x".repeat(2100)+"</s:text></s:failed-assert>"),"message").length()));
  t.test("Diagnostic id bounded",()->eq(200,Values.text(diagnostic("<s:failed-assert id='"+"x".repeat(250)+"'/>"),"rule").length()));
  t.test("Diagnostic count bounded",()->eq(100,diagnostics("<s:failed-assert/>".repeat(120)).size()));
  t.test("Diagnostic whitespace normalized",()->eq("Check",Values.text(diagnostic("<s:failed-assert><s:text>  Check  </s:text></s:failed-assert>"),"message")));
  manifestTest(t,"Wrong validator version refused",(d,m)->m.put("version","1.6.2"));
  manifestTest(t,"Wrong rules release refused",(d,m)->m.put("configuration","2026-01-31"));
  manifestTest(t,"Changed JAR refused",(d,m)->m.put("jarSha256","0".repeat(64)));
  manifestTest(t,"Empty configuration hashes refused",(d,m)->m.put("configurationFiles",Map.of()));
  manifestTest(t,"Changed schema refused",(d,m)->Json.map(m,"configurationFiles").put("scenarios.xml","0".repeat(64)));
  manifestTest(t,"Manifest escape refused",(d,m)->Json.map(m,"configurationFiles").put("../outside.xml","0".repeat(64)));
  manifestTest(t,"Unrecorded configuration file refused",(d,m)->{try{Files.writeString(d.resolve("config/extra.xml"),"<extra/>");}catch(Exception e){throw new RuntimeException(e);}});
  t.test("Initial validation attempt retained",()->{try(Fixtures f=new Fixtures()){String id=f.intake("01-ubl-office.xml");eq(1,Json.list(f.get(id),"validationHistory").size());}});
  t.test("Revalidation appends instead of replacing history",()->{try(Fixtures f=new Fixtures()){String id=f.intake("01-ubl-office.xml");String first=Json.canonical(validation(f,id));f.service.revalidate(f.a("reviewer"),id,f.version(id));var list=Json.list(f.get(id),"validationHistory");eq(2,list.size());eq(first,Json.canonical(list.getFirst()));}});
  t.test("Revalidation preserves original hash",()->{try(Fixtures f=new Fixtures()){String id=f.intake("01-ubl-office.xml"),sha=Values.text(f.get(id),"sha256");f.service.revalidate(f.a("reviewer"),id,f.version(id));eq(sha,Values.text(f.get(id),"sha256"));}});
  t.test("Unavailable historical report is explicit",()->{try(Fixtures f=new Fixtures()){String id=f.intake("01-ubl-office.xml");error("REPORT_UNAVAILABLE",()->f.service.report(f.a("auditor"),id,0));}});
  t.test("Out-of-range historical report is explicit",()->{try(Fixtures f=new Fixtures()){String id=f.intake("01-ubl-office.xml");error("REPORT_UNAVAILABLE",()->f.service.report(f.a("auditor"),id,9));}});
  t.test("Historical report stored as encrypted blob",()->{try(Fixtures f=new Fixtures(true,Fixtures.stub("PASS"))){String id=f.intake("01-ubl-office.xml");eq("<report synthetic=\"true\"/>",new String(f.service.report(f.a("auditor"),id,0),StandardCharsets.UTF_8));}});
  t.test("Evidence contains historical report",()->{try(Fixtures f=new Fixtures(true,Fixtures.stub("PASS"))){String id=f.intake("01-ubl-office.xml");Set<String> entries=new HashSet<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(f.service.evidence(f.a("auditor"),id)))){for(ZipEntry e;(e=z.getNextEntry())!=null;)entries.add(e.getName());}yes(entries.contains("validation-history/report-1.xml"));}});
  t.test("Revalidation history cannot bypass attempt bound",()->{try(Fixtures f=new Fixtures()){String id=f.intake("01-ubl-office.xml");f.store.write(s->{var i=Json.object(Store.table(s,"invoices").get(id));i.put("validationHistory",new ArrayList<>(Collections.nCopies(100,Json.copy(i.get("validation")))));return null;});error("VALIDATION_HISTORY_LIMIT",()->f.service.revalidate(f.a("reviewer"),id,f.version(id)));}});
  t.test("Export contains explicit validation provenance",()->{try(Fixtures f=new Fixtures()){String id=f.approved("01-ubl-office.xml");var j=f.queue(id);var payload=f.store.read(s->Json.map(Json.object(Store.table(s,"jobs").get(Values.text(j,"id"))),"payload"));yes(payload.containsKey("validationEvidence"));yes(Json.stringify(payload).contains("UNAVAILABLE"));}});
  t.finish("build/test-results/validation.xml");
 }
}
