package fun.eqad.eskin.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpUtil {
    private static final String USER_AGENT = "MineSkin-User-Agent";

    public static String getJson(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);

        int responseCode = connection.getResponseCode();
        if (responseCode == 204 || responseCode == 404) {
            return null;
        }

        if (responseCode != 200) {
            throw new IOException("请求失败: " + responseCode);
        }

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            String result = response.toString();
            if (result.trim().isEmpty() || result.trim().equals("{}")) {
                return null;
            }
            return result;
        }
    }

    public static String postJson(String urlString, String jsonData) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        byte[] postData = jsonData.getBytes(StandardCharsets.UTF_8);
        connection.getOutputStream().write(postData);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("请求失败: " + responseCode);
        }

        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            return response.toString();
        }
    }
}
