package decapsulation;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/// Decapsulation by self-attaching a Java agent.
class AttachJavaAgent extends Decapsulater {
  public static void main(String[] args) {
    new AttachJavaAgent().run();
  }

  @Override
  Lookup makeLookup() throws Exception {
    Path jarPath = createJar();

    // start a new process, because we cannot self-attach
    Path agentLoader = Files.createTempFile("JavaAgentLoader", ".java");
    Files.writeString(agentLoader,
        "public class JavaAgentLoader {" +
            "  public static void main(String[] args) throws Exception {" +
            "    com.sun.tools.attach.VirtualMachine.attach(args[0]).loadAgent(args[1]);" +
            "  }" +
            "}");
    agentLoader.toFile().deleteOnExit();
    new ProcessBuilder()
        .command(System.getProperty("java.home") + "/bin/java",
            agentLoader.toString(),
            Long.toString(ProcessHandle.current().pid()),
            jarPath.toString())
        .inheritIO() // to see output for debugging
        .start()
        .waitFor();

    Field implLookup = Lookup.class.getDeclaredField("IMPL_LOOKUP");
    implLookup.setAccessible(true);
    return (Lookup) implLookup.get(null);
  }

  // create the agent jar.
  // only contains the manifest, because the Agent class is already on the classpath
  private static Path createJar() throws IOException {
    Path path = Files.createTempFile("decapsulation", ".jar").toAbsolutePath();
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(path.toFile()))) {
      jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
      jos.write("Agent-Class: decapsulation.Agent".getBytes());
      jos.closeEntry();
    }
    path.toFile().deleteOnExit();
    return path;
  }
}

class Agent {
  public static void agentmain(String arg, Instrumentation instrumentation) {
    // open java.lang.invoke so that AttachJavaAgent can access Lookup internals,
    // as if --add-opens was given on the command line.
    Module unnamedModule = Agent.class.getModule(); // module where our other code lives too
    var extraOpens = Map.of("java.lang.invoke", Set.of(unnamedModule));
    instrumentation.redefineModule(Lookup.class.getModule(),
        Set.of(), Map.of(), extraOpens, Set.of(), Map.of());
  }
}
