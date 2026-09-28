package decapsulation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Files;
import java.nio.file.Path;

/// Decapsulation by self-attaching a JVMTI agent.
class AttachJvmtiAgent extends Decapsulater {
  static Lookup LOOKUP = MethodHandles.lookup();

  public static void main(String[] args) {
    new AttachJvmtiAgent().run();
  }

  @Override
  Lookup makeLookup() throws Throwable {
    // start a new process, because we cannot self-attach
    Path agentLoader = Files.createTempFile("JvmtiAgentLoader", ".java");
    Files.writeString(agentLoader,
        "public class JvmtiAgentLoader {" +
        "  public static void main(String[] args) throws Exception {" +
        "    com.sun.tools.attach.VirtualMachine.attach(args[0]).loadAgentPath(args[1]);" +
        "  }" +
        "}");
    agentLoader.toFile().deleteOnExit();
    new ProcessBuilder()
        .command(System.getProperty("java.home") + "/bin/java",
            agentLoader.toString(),
            Long.toString(ProcessHandle.current().pid()),
            System.getProperty("os.name").startsWith("Windows")
                ? "native/jvmtiagent.dll" : "native/jvmtiagent.so")
        .inheritIO() // to see output for debugging
        .start()
        .waitFor();

    return LOOKUP;
  }
}
