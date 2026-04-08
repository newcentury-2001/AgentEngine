package com.agentcommon.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class HttpRequestClient {

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    @RepairEncoding
    public String get(String url, Map<String, String> headers) {
        try {
            URL targetUrl = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
            try {
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                int responseCode = conn.getResponseCode();
                if (responseCode / 100 != 2) {
                    throw new IOException("HTTP request failed with status: " + responseCode);
                }
                return readResponse(conn.getInputStream());
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            throw new RuntimeException("HTTP GET request failed: " + url, e);
        }
    }

    @RepairEncoding
    public String post(String url, String body, Map<String, String> headers) {
        try {
            URL targetUrl = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
            try {
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setDoOutput(true);
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                if (body != null && !body.isEmpty()) {
                    conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                }
                int responseCode = conn.getResponseCode();
                if (responseCode / 100 != 2) {
                    String errorBody = readErrorStream(conn.getErrorStream());
                    throw new IOException("HTTP POST request failed with status: " + responseCode + ", body: " + errorBody);
                }
                return readResponse(conn.getInputStream());
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            throw new RuntimeException("HTTP POST request failed: " + url, e);
        }
    }

    @RepairEncoding
    public String post(HttpClient client, String url, String body, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
        }

        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP POST request failed with status: " + response.statusCode() +
                    ", body: " + response.body());
        }
        return response.body();
    }

    @RepairEncoding
    public String send(HttpClient client, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP request failed with status: " + response.statusCode() + ", body: " + response.body());
        }
        return response.body();
    }

    private String readResponse(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString();
        }
    }

    private String readErrorStream(InputStream errorStream) {
        if (errorStream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
            return response.toString();
        } catch (IOException e) {
            return "unable to read error stream: " + e.getMessage();
        }
    }
}
