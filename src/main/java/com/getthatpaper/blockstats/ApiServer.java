package com.getthatpaper.blockstats;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight HTTP server exposing block stats as JSON.
 */
public class ApiServer {
    private final DataStore dataStore;
    private final Logger logger;
    private final String host;
    private final int port;
    private final String contextPath;
    private HttpServer server;

    public ApiServer(String host, int port, String contextPath, DataStore dataStore, Logger logger) {
        this.host = host;
        this.port = port;
        this.contextPath = normalizeContext(contextPath);
        this.dataStore = dataStore;
        this.logger = logger;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext(contextPath + "/blocks/top", this::handleTopBlocks);
        server.createContext(contextPath + "/blocks/player", this::handlePlayerBlocks);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        logger.info("REST API running at http://" + host + ":" + port + contextPath);
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private void handleTopBlocks(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeCors(exchange, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeCors(exchange, 405, jsonMessage("Only GET allowed"));
            return;
        }
        int limit = parseLimit(exchange.getRequestURI().getRawQuery());
        try {
            List<MaterialStat> stats = dataStore.getTopBlocks(limit);
            String body = toBlocksJson(stats, limit, null);
            writeCors(exchange, 200, body);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to query top blocks", ex);
            writeCors(exchange, 500, jsonMessage("Database error"));
        }
    }

    private void handlePlayerBlocks(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeCors(exchange, 204, "");
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeCors(exchange, 405, jsonMessage("Only GET allowed"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String base = contextPath + "/blocks/player/";
        if (!path.startsWith(base) || !path.endsWith("/top")) {
            writeCors(exchange, 404, jsonMessage("Not found"));
            return;
        }
        String encodedName = path.substring(base.length(), path.length() - "/top".length());
        String playerName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        int limit = parseLimit(exchange.getRequestURI().getRawQuery());
        try {
            if (!dataStore.hasPlayer(playerName)) {
                writeCors(exchange, 404, jsonMessage("Player not found"));
                return;
            }
            List<MaterialStat> stats = dataStore.getTopBlocksForPlayerName(playerName, limit);
            String body = toBlocksJson(stats, limit, playerName);
            writeCors(exchange, 200, body);
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to query player blocks", ex);
            writeCors(exchange, 500, jsonMessage("Database error"));
        }
    }

    private String toBlocksJson(List<MaterialStat> stats, int limit, String playerName) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        if (playerName != null) {
            sb.append("\"player\":\"").append(escape(playerName)).append("\",");
        }
        sb.append("\"limit\":").append(limit).append(',');
        sb.append("\"blocks\":[");
        for (int i = 0; i < stats.size(); i++) {
            MaterialStat stat = stats.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"material\":\"").append(escape(stat.material())).append("\",")
                    .append("\"count\":").append(stat.count())
                    .append('}');
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private int parseLimit(String rawQuery) {
        int defaultLimit = 10;
        if (rawQuery == null || rawQuery.isEmpty()) {
            return defaultLimit;
        }
        for (String part : rawQuery.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "limit".equalsIgnoreCase(kv[0])) {
                try {
                    int value = Integer.parseInt(kv[1]);
                    if (value > 0 && value <= 1000) {
                        return value;
                    }
                } catch (NumberFormatException ignored) {
                    return defaultLimit;
                }
            }
        }
        return defaultLimit;
    }

    private void writeCors(HttpExchange exchange, int status, String body) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        byte[] data = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private String jsonMessage(String message) {
        return "{\"message\":\"" + escape(message) + "\"}";
    }

    private String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String normalizeContext(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        String result = context.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
