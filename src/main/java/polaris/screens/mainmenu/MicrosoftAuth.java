package polaris.screens.mainmenu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public final class MicrosoftAuth {

    // Официальный идентификатор приложения (Client ID) и адрес перенаправления на локальный HttpServer
    private static final String CLIENT_ID = "61e389c9-e770-496c-b39b-ff70f0653d9e";
    private static final String REDIRECT_URI = "http://localhost:56964/login";

    // AUTH_URL ссылка для автоматического запуска интернет-браузера игрока
    public static final String AUTH_URL = "https://live.com" +
            "?client_id=" + CLIENT_ID +
            "&response_type=code" +
            "&redirect_uri=" + REDIRECT_URI +
            "&scope=XboxLive.signin%20offline_access";

    private static final Gson gson = new Gson();

    /**
     * Асинхронный метод обмена временного кода из браузера на GameProfile под архитектуру Player API 1.21.11
     */
    public static void loginWithCode(String code, Consumer<GameProfile> callback) {
        new Thread(() -> {
            try {
                // Шаг 1: Обмениваем полученный код авторизации (Auth Code) на Microsoft Access Token
                String decodedCode = URLDecoder.decode(code, StandardCharsets.UTF_8);
                String msToken = getMicrosoftToken(decodedCode);
                if (msToken == null) { callback.accept(null); return; }

                // Шаг 2: Аутентифицируемся в Xbox Live с помощью Microsoft Token
                JsonObject xblAuth = authXboxLive(msToken);
                if (xblAuth == null) { callback.accept(null); return; }
                String xblToken = xblAuth.get("Token").getAsString();
                String uhs = xblAuth.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

                // Шаг 3: Проходим авторизацию в XSTS (Xbox Secure Token Service)
                String xstsToken = authXSTS(xblToken);
                if (xstsToken == null) { callback.accept(null); return; }

                // Шаг 4: Меняем XSTS токен на постоянный токен сессии профиля Minecraft
                JsonObject mcSession = authMinecraft(uhs, xstsToken);
                if (mcSession == null) { callback.accept(null); return; }
                String mcAccessToken = mcSession.get("access_token").getAsString();

                // Шаг 5: Запрашиваем официальный игровой профиль (проверяем лицензию, получаем UUID и никнейм)
                JsonObject profileJson = getMinecraftProfile(mcAccessToken);
                if (profileJson == null) { callback.accept(null); return; }

                String username = profileJson.get("name").getAsString();
                String uuidStr = profileJson.get("id").getAsString();

                // Преобразуем строковый UUID без дефисов в стандартный объект java.util.UUID
                UUID uuid = UUID.fromString(uuidStr.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                        "$1-$2-$3-$4-$5"
                ));

                // Формируем готовый современный объект GameProfile игрока взамен старого User/Session
                GameProfile profile = new GameProfile(uuid, username);

                callback.accept(profile);
            } catch (Exception e) {
                e.printStackTrace();
                callback.accept(null);
            }
        }).start();
    }

    private static String getMicrosoftToken(String code) throws Exception {
        URL url = new URL("https://live.com");
        String postData = "client_id=" + CLIENT_ID +
                "&code=" + code +
                "&grant_type=authorization_code" +
                "&redirect_uri=" + REDIRECT_URI;

        JsonObject json = sendPostRequest(url, postData, "application/x-www-form-urlencoded", null);
        return json != null && json.has("access_token") ? json.get("access_token").getAsString() : null;
    }

    private static JsonObject authXboxLive(String msToken) throws Exception {
        URL url = new URL("https://xboxlive.com");
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "://xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + msToken);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://xboxlive.com");
        body.addProperty("TokenType", "JWT");

        return sendPostRequest(url, gson.toJson(body), "application/json", null);
    }

    private static String authXSTS(String xblToken) throws Exception {
        URL url = new URL("https://xboxlive.com");

        com.google.gson.JsonArray tokens = new com.google.gson.JsonArray();
        tokens.add(xblToken);

        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", tokens);

        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://://minecraftservices.com");
        body.addProperty("TokenType", "JWT");

        JsonObject json = sendPostRequest(url, gson.toJson(body), "application/json", null);
        return json != null && json.has("Token") ? json.get("Token").getAsString() : null;
    }

    private static JsonObject authMinecraft(String uhs, String xstsToken) throws Exception {
        URL url = new URL("https://://minecraftservices.comauthentication/login_with_xbox");
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);

        return sendPostRequest(url, gson.toJson(body), "application/json", null);
    }

    private static JsonObject getMinecraftProfile(String mcToken) throws Exception {
        URL url = new URL("https://://minecraftservices.comminecraft/profile");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + mcToken);

        if (conn.getResponseCode() == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return gson.fromJson(reader, JsonObject.class);
            }
        }
        return null;
    }

    private static JsonObject sendPostRequest(URL url, String data, String contentType, String authHeader) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Accept", "application/json");
        if (authHeader != null) {
            conn.setRequestProperty("Authorization", authHeader);
        }
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(data.getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return gson.fromJson(reader, JsonObject.class);
            }
        }
        return null;
    }
}
