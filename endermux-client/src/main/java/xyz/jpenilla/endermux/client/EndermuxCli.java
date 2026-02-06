package xyz.jpenilla.endermux.client;

import java.util.concurrent.Callable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import xyz.jpenilla.endermux.client.runtime.EndermuxClient;

@Command(
  name = "endermux-client",
  mixinStandardHelpOptions = true,
  description = "Endermux Client - Fully-featured remote console experience for Minecraft servers implementing the Endermux protocol."
)
@NullMarked
public final class EndermuxCli implements Callable<Integer> {

  @Option(
    names = {"--socket", "-s"},
    defaultValue = "console.sock",
    description = "Path to the console socket."
  )
  private String socketPath = "console.sock";

  static void main(final String[] args) {
    final int exitCode = new CommandLine(new EndermuxCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    try {
      new EndermuxClient().run(this.socketPath);
      return 0;
    } catch (final Exception e) {
      System.err.println("Error starting Endermux client: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }
}
