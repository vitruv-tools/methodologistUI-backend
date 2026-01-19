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
        Long userId = extractUserId(session);
        Long projectId = extractProjectId(session);

        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("userId required"));
            return;
        }

        Path sessionDir = Files.createTempDirectory("lsp-session-" + session.getId());
        Path userProject = sessionDir.resolve("UserProject");
        Path modelDir = userProject.resolve("model");
        Files.createDirectories(modelDir);

        List<MetaModel> metamodels = metaModelService.findAccessibleByUserOrProject(userId, projectId);

        for (MetaModel mm : metamodels) {
            byte[] ecoreData = mm.getEcoreFile().getData();
            String fileName = mm.getEcoreFile().getFilename();
            Path ecoreFile = modelDir.resolve(fileName);
            Files.write(ecoreFile, ecoreData);
        }

        String jarPath = new File("src/main/resources/lsp/tools.vitruv.dsls.reactions.ide.jar")
                .getAbsolutePath();

        ProcessBuilder pb = new ProcessBuilder(
                "java",
                "-jar", jarPath,
                "-log", "-trace");
        pb.directory(userProject.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        LspServerProcess lspProcess = new LspServerProcess(
                session, process, writer, reader, sessionDir, userProject);
        sessions.put(session.getId(), lspProcess);

        new Thread(() -> lspProcess.readFromLsp()).start();

        new Thread(() -> {
            try {
                Thread.sleep(500);

                String rootUriMessage = String.format(
                        "{\"type\":\"workspaceReady\",\"rootUri\":\"%s\"}",
                        userProject.toUri().toString());
                session.sendMessage(new TextMessage(rootUriMessage));
            } catch (Exception e) {
                System.err.println("💥 Failed to send workspaceReady: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
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
        private final Path userProject;

        LspServerProcess(
                WebSocketSession session,
                Process process,
                BufferedWriter writer,
                BufferedReader reader,
                Path tempDir,
                Path userProject) {
            this.session = session;
            this.process = process;
            this.writer = writer;
            this.reader = reader;
            this.tempDir = tempDir;
            this.userProject = userProject;
        }

        void readFromLsp() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Content-Length:")) {
                        int contentLength = Integer.parseInt(line.split(":")[1].trim());

                        reader.readLine();

                        char[] content = new char[contentLength];
                        int read = reader.read(content, 0, contentLength);

                        String message = new String(content);

                        session.sendMessage(new TextMessage(message));
                    }
                }
            } catch (IOException e) {
                System.err.println("💥 LSP reader error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        void sendToLsp(String jsonMessage) throws IOException {
            String lspMessage = "Content-Length: " + jsonMessage.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n"
                    + jsonMessage;
            writer.write(lspMessage);
            writer.flush();
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

    private Long extractUserId(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                String userIdStr = extractQueryParam(query, "userId");
                if (userIdStr != null) {
                    Long userId = Long.parseLong(userIdStr);
                    logger.debug("Extracted userId from query parameter: {}", userId);
                    return userId;
                }
            }

            Object principal = session.getPrincipal();
            if (principal != null) {
                logger.debug("Principal type: {}", principal.getClass().getName());

                if (principal instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) {
                    org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt = (org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) principal;

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

                    Object userIdClaim = jwt.getToken().getClaim("userId");
                    if (userIdClaim != null) {
                        Long userId = Long.parseLong(userIdClaim.toString());
                        logger.debug("Extracted userId from JWT 'userId' claim: {}", userId);
                        return userId;
                    }

                    String email = jwt.getToken().getClaim("preferred_username");
                    if (email == null) {
                        email = jwt.getToken().getClaim("email");
                    }
                    if (email != null) {
                        logger.debug("Found email in JWT: {}, need to lookup userId", email);
                    }
                }

                logger.debug("Principal toString: {}", principal);
            }

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

    private Long extractProjectId(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("projectId=")) {
                String projectIdStr = extractQueryParam(query, "projectId");
                if (projectIdStr != null) {
                    Long projectId = Long.parseLong(projectIdStr);
                    logger.debug("Extracted projectId from query parameter: {}", projectId);
                    return projectId;
                }
            }

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