import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Parser check only. It intentionally does not pretend to replace an Android build. */
public class CheckJavaSyntax {
    public static void main(String[] args)throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(var files=compiler.getStandardFileManager(diagnostics,null,null);var paths=Files.walk(Path.of("app/src/main/java"))){
            var source=paths.filter(p->p.toString().endsWith(".java")).toList();
            JavacTask task=(JavacTask)compiler.getTask(null,files,diagnostics,List.of("--release","17","-proc:none"),null,files.getJavaFileObjectsFromPaths(source));
            task.parse();
            for(var diagnostic:diagnostics.getDiagnostics())if(diagnostic.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(diagnostic.toString());
            System.out.println("PASS: parsed "+source.size()+" Java files (syntax only; Android types not resolved)");
        }
    }
}
