package com.agentcommon.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP 请求工具类
 * <p>
 * 所有返回 String 的方法会自动通过 AOP 进行编码修复。
 * 如果响应是 JSON，会递归检查每个字段，只要任意字段乱码就修复整个 JSON。
 */
public class HttpHelper {

    private static final Logger log = LoggerFactory.getLogger(HttpHelper.class);
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private HttpHelper() {
    }

    /**
     * 发送 GET 请求，返回原始字符串
     *
     * @param url     请求 URL
     * @param headers 请求头（可为 null）
     * @return 原始响应字符串
     */
    public static String get(String url, Map<String, String> headers) {
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
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP request failed with status: " + responseCode);
                }

                return readResponse(conn.getInputStream());
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            log.error("HTTP GET request failed: {}", url, e);
            throw new RuntimeException("HTTP GET request failed: " + url, e);
        }
    }

    /**
     * 发送 GET 请求，返回原始字符串（无自定义请求头）
     */
    public static String get(String url) {
        return get(url, null);
    }

    private static String readResponse(InputStream inputStream) throws IOException {
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
}
