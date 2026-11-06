package tools.vitruv.methodologist.lsp;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A language server process bound to a single WebSocket session. Implementations own the
 * server-specific LSP framing and error handling.
 */
public interface LspSessionProcess {

  /**
   * Forwards a JSON-RPC message from the client to the language server.
   *
   * @param message the JSON payload received over the WebSocket
   * @throws IOException if the message cannot be written to the process
   */
  void sendToLsp(String message) throws IOException;

  /** Reads messages from the language server and forwards them to the WebSocket session. */
  void readFromLsp();

  /** Stops the language server process and releases its streams. */
  void destroy();

  /**
   * Returns the session's temporary workspace directory, deleted when the session is cleaned up.
   *
   * @return the temporary directory, or {@code null} if there is none
   */
  Path tempDir();
}
