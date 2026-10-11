package io.ahad.rechnungflow.ingest;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.core.FileStore;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
/** Pinned local KoSIT worker; no remote validation endpoint and no shell interpolation. */
public final class KositEngine implements ValidationEngine {
    private final Path jar,scenarios,work;private final String jarHash,configurationHash;private final int timeoutSeconds;
    private final Semaphore slots=new Semaphore(2);
    public KositEngine(Path directory,Path workingDirectory,int timeoutSeconds)throws IOException{
        Path manifest=directory.resolve("manifest.json");var m=Json.obj(Files.readString(manifest));
        Problem.require("1.6.3".equals(Values.text(m,"version"))&&"2026-08-31".equals(Values.text(m,"configuration")),500,"VALIDATOR_PIN_MISMATCH");
        jar=directory.resolve("validator-1.6.3-standalone.jar").toAbsolutePath();scenarios=directory.resolve("config/scenarios.xml").toAbsolutePath();
        jarHash=Crypto.sha(Files.readAllBytes(jar));Problem.require(Crypto.equal(jarHash,Values.text(m,"jarSha256")),500,"VALIDATOR_PIN_MISMATCH");
        var hashes=Json.map(m,"configurationFiles");Problem.require(!hashes.isEmpty(),500,"VALIDATOR_PIN_MISMATCH");
        Path config=directory.resolve("config").toAbsolutePath().normalize();
        for(var e:hashes.entrySet()){Path f=config.resolve(e.getKey()).normalize();Problem.require(f.startsWith(config)&&!Files.isSymbolicLink(f)&&Files.isRegularFile(f),500,"VALIDATOR_PIN_MISMATCH");
            Problem.require(Crypto.equal(Crypto.sha(Files.readAllBytes(f)),String.valueOf(e.getValue())),500,"VALIDATOR_PIN_MISMATCH");}
        try(var paths=Files.walk(config)){
            var actual=new HashSet<String>();for(Path f:paths.toList()){
                Problem.require(!Files.isSymbolicLink(f),500,"VALIDATOR_PIN_MISMATCH");
                if(Files.isRegularFile(f))actual.add(config.relativize(f).toString().replace('\\','/'));
            }
            Problem.require(actual.equals(hashes.keySet())&&actual.contains("scenarios.xml"),500,"VALIDATOR_PIN_MISMATCH");
        }
        configurationHash=Crypto.sha(Json.canonical(hashes));work=workingDirectory.toAbsolutePath();Files.createDirectories(work);FileStore.restrict(work,true);
        Problem.require(timeoutSeconds>=2&&timeoutSeconds<=120,500,"INVALID_CONFIGURATION");this.timeoutSeconds=timeoutSeconds;
    }
    public Map<String,Object> capabilities(){return Values.map("configured",true,"engine","KoSIT","version","1.6.3","configuration","2026-08-31","jarSha256",jarHash,"configurationSha256",configurationHash);}
    public Map<String,Object> validate(byte[] original){
        var result=Values.map("status","ERROR","engine","KoSIT","version","1.6.3","configuration","2026-08-31","jarSha256",jarHash,"configurationSha256",configurationHash,"issues",List.of(),"report","");
        if(!slots.tryAcquire()){result.put("status","BUSY");return result;}Path dir=null;Process process=null;
        try{
            SafeXml.parse(original);dir=Files.createTempDirectory(work,"validation-");FileStore.restrict(dir,true);Path in=dir.resolve("invoice.xml"),out=dir.resolve("result.json");Files.write(in,original);FileStore.restrict(in,false);
            String javaExe=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
            String own=Path.of(KositWorker.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            ProcessBuilder pb=new ProcessBuilder(javaExe,"-Xmx256m","-cp",own+File.pathSeparator+jar,"io.ahad.rechnungflow.ingest.KositWorker",in.toString(),scenarios.toString(),out.toString());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            // A plugin must not receive application accounting credentials or encryption keys.
            for(String k:new ArrayList<>(pb.environment().keySet()))if(k.startsWith("RF_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("JDK_JAVA_OPTIONS")||k.equals("_JAVA_OPTIONS"))pb.environment().remove(k);
            process=pb.start();if(!process.waitFor(timeoutSeconds,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);result.put("status","TIMEOUT");return result;}
            if(process.exitValue()!=0||!Files.isRegularFile(out)||Files.size(out)>8_388_608)return result;
            var data=Json.object(Json.parseStored(Files.readString(out)));String status=Values.text(data,"status");
            if(!Set.of("PASS","FAIL","ERROR").contains(status))return result;
            String report=Values.text(data,"report");if(report.isBlank())return result;
            var reportDocument=SafeXml.parse(report.getBytes(java.nio.charset.StandardCharsets.UTF_8),8_388_608);
            result.put("issues",ReportDiagnostics.extract(reportDocument));
            result.put("status",status);result.put("report",report);result.put("recommendation",Values.text(data,"recommendation"));return result;
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return result;}
        finally{
            if(process!=null&&process.isAlive())process.destroyForcibly();
            if(dir!=null)try(var walk=Files.walk(dir)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}catch(IOException ignored){}
            slots.release();
        }
    }
}
