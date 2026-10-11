package io.ahad.rechnungflow.server;
import io.ahad.rechnungflow.core.*;import io.ahad.rechnungflow.ingest.*;import java.nio.file.*;import java.util.*;
/** Command-line official acceptance boundary. Does not use the synthetic test doubles. */
public final class ValidateDocument {
    public static void main(String[]args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("Usage: ValidateDocument KOSIT_DIR INPUT_XML OUTPUT_JSON");
        Path input=Path.of(args[1]),output=Path.of(args[2]);Problem.require(Files.size(input)<=SafeXml.MAX_BYTES,413,"FILE_TOO_LARGE");
        Path work=Files.createTempDirectory("rf-official-validation-");
        try{var engine=new KositEngine(Path.of(args[0]),work,120);var answer=engine.validate(Files.readAllBytes(input));
            Files.createDirectories(output.toAbsolutePath().getParent());Files.writeString(output,Json.stringify(answer));
            System.out.println("KoSIT result: "+Values.text(answer,"status"));
        }finally{try(var walk=Files.walk(work)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
