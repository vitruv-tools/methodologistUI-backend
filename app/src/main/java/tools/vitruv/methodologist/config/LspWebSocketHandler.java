package tools.vitruv.methodologist.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.service.MetaModelService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Component
public class LspWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private MetaModelService metaModelService;

    private static final Logger logger = LoggerFactory.getLogger(LspWebSocketHandler.class);
    private final ConcurrentHashMap<String, LspServerProcess> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("🔵 WebSocket connection established: " + session.getId());

        // 1. User/Project extrahieren
        Long userId = extractUserId(session);
        Long projectId = extractProjectId(session);

        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("userId required"));
            return;
        }

        System.out.println("👤 User: " + userId + ", Project: " + projectId);

        // 2. Workspace-Struktur erstellen
        Path sessionDir = Files.createTempDirectory("lsp-session-" + session.getId());
        Path userProject = sessionDir.resolve("UserProject");
        Path modelDir = userProject.resolve("model");
        Files.createDirectories(modelDir);

        System.out.println("📂 Workspace: " + userProject.toAbsolutePath());

        // 3. Metamodelle schreiben
        List<MetaModel> metamodels = metaModelService.findAccessibleByUserOrProject(userId, projectId);
        System.out.println("🔍 Found " + metamodels.size() + " metamodels");

        for (MetaModel mm : metamodels) {
            byte[] ecoreData = mm.getEcoreFile().getData();
            String fileName = mm.getEcoreFile().getFilename();
            Path ecoreFile = modelDir.resolve(fileName);
            Files.write(ecoreFile, ecoreData);

            String content = new String(ecoreData, StandardCharsets.UTF_8);
            if (content.contains("nsURI=\"")) {
                int start = content.indexOf("nsURI=\"") + 7;
                int end = content.indexOf("\"", start);
                String nsURI = content.substring(start, end);
                System.out.println("  ✅ " + fileName + " (" + ecoreData.length + " bytes)");
                System.out.println("     📌 nsURI: " + nsURI);
            } else {
                System.out.println("  ⚠️ " + fileName + " - NO nsURI FOUND!");
            }
        }

        System.out.println("📂 Verifying files in: " + modelDir.toAbsolutePath());
        try (var stream = Files.list(modelDir)) {
            stream.forEach(file -> {
                try {
                    System.out.println("  📄 " + file.getFileName() + " (" + Files.size(file) + " bytes, exists: "
                            + Files.exists(file) + ")");
                } catch (IOException e) {
                    System.out.println("  ❌ Error reading: " + file.getFileName());
                }
            });
        }

        // 4. LSP starten
        String jarPath = new File("src/main/resources/lsp/tools.vitruv.dsls.reactions.ide.jar")
                .getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-jar", jarPath,
                "-log", "-trace");
        pb.directory(userProject.toFile()); // ← Working Dir = UserProject!
        pb.redirectErrorStream(true);

        Process process = pb.start();
        System.out.println("✅ LSP started, PID: " + process.pid());

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // 5. Session speichern (VOR dem Reader-Thread!)
        LspServerProcess lspProcess = new LspServerProcess(
                session, process, writer, reader, sessionDir, userProject);
        sessions.put(session.getId(), lspProcess);

        // 6. Reader Thread starten
        new Thread(() -> lspProcess.readFromLsp()).start();

        // ✅ 7. workspaceReady in separatem Thread senden (mit Verzögerung für
        // LSP-Start)
        new Thread(() -> {
            try {
                Thread.sleep(500); // LSP Zeit geben zum Starten und .ecore-Dateien zu scannen

                String rootUriMessage = String.format(
                        "{\"type\":\"workspaceReady\",\"rootUri\":\"%s\"}",
                        userProject.toUri().toString());
                System.out.println("📢 Sending workspaceReady to frontend");
                session.sendMessage(new TextMessage(rootUriMessage));
            } catch (Exception e) {
                System.err.println("💥 Failed to send workspaceReady: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();

        System.out.println("✅ Setup complete");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        LspServerProcess serverProcess = sessions.get(session.getId());
        if (serverProcess != null) {
            try {
                serverProcess.sendToLsp(message.getPayload());
            } catch (IOException e) {
                logger.error("Failed to send message to LSP", e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        LspServerProcess serverProcess = sessions.remove(session.getId());
        if (serverProcess != null) {
            serverProcess.destroy();

            // Temp-Workspace löschen
            if (serverProcess.tempDir != null && Files.exists(serverProcess.tempDir)) {
                Files.walk(serverProcess.tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Cleanup failed: {}", path);
                            }
                        });
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session: {}", session.getId(), exception);
        LspServerProcess serverProcess = sessions.remove(session.getId());
        if (serverProcess != null) {
            serverProcess.destroy();
        }
    }

    private class LspServerProcess {
        final WebSocketSession session;
        final Process process;
        final BufferedWriter writer;
        final BufferedReader reader;
        private final Path tempDir;
        private final Path userProject; // ← NEU hinzugefügt

        // ✅ Aktualisierter Konstruktor mit userProject Parameter
        LspServerProcess(
                WebSocketSession session,
                Process process,
                BufferedWriter writer,
                BufferedReader reader,
                Path tempDir,
                Path userProject) { // ← NEU
            this.session = session;
            this.process = process;
            this.writer = writer;
            this.reader = reader;
            this.tempDir = tempDir;
            this.userProject = userProject; // ← NEU
        }

        void readFromLsp() {
            try {
                System.out.println("📖 LSP reader thread started");
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("📥 LSP line: " + line);

                    if (line.startsWith("Content-Length:")) {
                        int contentLength = Integer.parseInt(line.split(":")[1].trim());
                        System.out.println("📏 Content-Length: " + contentLength);

                        reader.readLine(); // Skip empty line

                        char[] content = new char[contentLength];
                        int read = reader.read(content, 0, contentLength);
                        System.out.println("📖 Read " + read + " chars");

                        String message = new String(content);
                        System.out.println(
                                "📤 Sending to frontend: " + message.substring(0, Math.min(200, message.length())));

                        session.sendMessage(new TextMessage(message));
                    }
                }
                System.out.println("⚠️ LSP reader loop ended");
            } catch (IOException e) {
                System.err.println("💥 LSP reader error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        void sendToLsp(String jsonMessage) throws IOException {
            String lspMessage = "Content-Length: " + jsonMessage.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                    + jsonMessage;
            System.out.println(
                    "📤 Sending to LSP: " + jsonMessage.substring(0, Math.min(200, jsonMessage.length())));
            writer.write(lspMessage);
            writer.flush();
            System.out.println("✅ Sent and flushed");
        }

        void destroy() {
            try {
                writer.close();
            } catch (IOException e) {
                logger.error("Error closing LSP writer", e);
            }
            process.destroy();
        }
    }

    /**
     * Extracts the user ID from the WebSocket session.
     * 
     * <p>
     * Tries multiple strategies in order:
     * <ol>
     * <li>Query parameter: ?userId=123</li>
     * <li>JWT token (from Spring Security principal)</li>
     * <li>Session attributes</li>
     * </ol>
     *
     * @param session the WebSocket session
     * @return the user ID, or null if not found
     */
    private Long extractUserId(WebSocketSession session) {
        try {
            // Strategy 1: Query parameter
            String query = session.getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                String userIdStr = extractQueryParam(query, "userId");
                if (userIdStr != null) {
                    Long userId = Long.parseLong(userIdStr);
                    logger.debug("Extracted userId from query parameter: {}", userId);
                    return userId;
                }
            }

            // Strategy 2: JWT Token (Spring Security Principal)
            Object principal = session.getPrincipal();
            if (principal != null) {
                logger.debug("Principal type: {}", principal.getClass().getName());

                // Option A: JwtAuthenticationToken (from Spring Security OAuth2)
                if (principal instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) {
                    org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt = (org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) principal;

                    // Try "sub" claim (standard JWT subject)
                    String sub = jwt.getToken().getClaim("sub");
                    if (sub != null) {
                        try {
                            Long userId = Long.parseLong(sub);
                            logger.debug("Extracted userId from JWT 'sub' claim: {}", userId);
                            return userId;
                        } catch (NumberFormatException e) {
                            logger.warn("JWT 'sub' claim is not a number: {}", sub);
                        }
                    }

                    // Try "userId" claim (custom claim)
                    Object userIdClaim = jwt.getToken().getClaim("userId");
                    if (userIdClaim != null) {
                        Long userId = Long.parseLong(userIdClaim.toString());
                        logger.debug("Extracted userId from JWT 'userId' claim: {}", userId);
                        return userId;
                    }

                    // Try "preferred_username" or "email" as fallback
                    String email = jwt.getToken().getClaim("preferred_username");
                    if (email == null) {
                        email = jwt.getToken().getClaim("email");
                    }
                    if (email != null) {
                        logger.debug("Found email in JWT: {}, need to lookup userId", email);
                        // TODO: Optional - lookup user by email
                        // User user = userRepository.findByEmailIgnoreCase(email);
                        // return user.getId();
                    }
                }

                // Option B: Other principal types
                logger.debug("Principal toString: {}", principal);
            }

            // Strategy 3: Session attributes
            Object userIdAttr = session.getAttributes().get("userId");
            if (userIdAttr != null) {
                Long userId = Long.parseLong(userIdAttr.toString());
                logger.debug("Extracted userId from session attributes: {}", userId);
                return userId;
            }

            logger.warn("Could not extract userId from WebSocket session. URI: {}", session.getUri());
            return null;

        } catch (Exception e) {
            logger.error("Error extracting userId from WebSocket session", e);
            return null;
        }
    }

    /**
     * Extracts the project ID from the WebSocket session.
     * 
     * <p>
     * This is optional and can be null if the user is not working in a specific
     * project context.
     * Currently only supports query parameter extraction.
     *
     * @param session the WebSocket session
     * @return the project ID, or null if not provided
     */
    private Long extractProjectId(WebSocketSession session) {
        try {
            // Strategy 1: Query parameter
            String query = session.getUri().getQuery();
            if (query != null && query.contains("projectId=")) {
                String projectIdStr = extractQueryParam(query, "projectId");
                if (projectIdStr != null) {
                    Long projectId = Long.parseLong(projectIdStr);
                    logger.debug("Extracted projectId from query parameter: {}", projectId);
                    return projectId;
                }
            }

            // Strategy 2: Session attributes
            Object projectIdAttr = session.getAttributes().get("projectId");
            if (projectIdAttr != null) {
                Long projectId = Long.parseLong(projectIdAttr.toString());
                logger.debug("Extracted projectId from session attributes: {}", projectId);
                return projectId;
            }

            logger.debug("No projectId found in WebSocket session (this is optional)");
            return null;

        } catch (Exception e) {
            logger.error("Error extracting projectId from WebSocket session", e);
            return null;
        }
    }

    /**
     * Helper method to extract a query parameter value from a query string.
     * 
     * @param query     the full query string (e.g., "userId=123&projectId=456")
     * @param paramName the parameter name to extract (e.g., "userId")
     * @return the parameter value, or null if not found
     */
    private String extractQueryParam(String query, String paramName) {
        if (query == null || paramName == null) {
            return null;
        }

        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }
}