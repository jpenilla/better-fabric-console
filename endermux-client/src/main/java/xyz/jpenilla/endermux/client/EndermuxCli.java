package xyz.jpenilla.endermux.client;

import java.util.concurrent.Callable;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import xyz.jpenilla.endermux.client.runtime.EndermuxClient;
import xyz.jpenilla.endermux.client.runtime.StreamRedirection;
import xyz.jpenilla.endermux.client.runtime.TerminalOutput;

@Command(
  name = "endermux-client",
  mixinStandardHelpOptions = true,
  description = "Endermux Client - Fully-featured remote console experience for Minecraft servers implementing the Endermux protocol."
)
@NullMarked
public final class EndermuxCli implements Callable<Integer> {
  private static final Logger LOGGER = LoggerFactory.getLogger(EndermuxCli.class);

  @Option(
    names = {"--socket", "-s"},
    defaultValue = "console.sock",
    description = "Path to the console socket."
  )
  private String socketPath = "console.sock";

  @Option(
    names = "--debug",
    defaultValue = "false",
    description = "Enable debug logging."
  )
  private boolean debug;

  static void main(final String[] args) {
    final int exitCode = new CommandLine(new EndermuxCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    this.configureLogging();
    try {
      new EndermuxClient().run(this.socketPath);
      return 0;
    } catch (final Exception e) {
      LOGGER.error("Error starting Endermux client", e);
      return 1;
    }
  }

  private void configureLogging() {
    TerminalOutput.captureOriginalStreams(System.out, System.err);
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    if (this.debug) {
      final LoggerConfig root = context.getConfiguration().getRootLogger();
      root.setLevel(Level.DEBUG);
      context.updateLoggers();
    }
    StreamRedirection.install();
  }
}
