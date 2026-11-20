package ru.mtproxy.picker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.json.JSONObject;

final class PanelClient {
    static final class HttpFailure extends IOException {
        final int code;
        HttpFailure(int code, String message) { super(message); this.code = code; }
    }
    static final class CertificateApproval extends IOException {
        final String fingerprint;
        CertificateApproval(String fingerprint) {
            super("Сертификат панели требует подтверждения");
            this.fingerprint = fingerprint;
        }
    }

    final String origin;
    String pin;
    private String session = "";
    private String csrf = "";
    private String untrusted;

    PanelClient(String address, String pin) throws Exception {
        this.origin = normalizeAddress(address);
        this.pin = pin;
    }

    static String normalizeAddress(String address) throws Exception {
        String value = address.trim();
        if (!value.contains("://")) value = "https://" + value;
        URI uri = new URI(value);
        String host = uri.getHost();
        String path = uri.getPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isEmpty()
                || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(path == null || path.isEmpty() || path.equals("/") || path.equals("/manage")
                    || path.equals("/manage/") || path.equals("/mange") || path.equals("/mange/"))) {
            throw new IllegalArgumentException("Укажите IP VPS или HTTPS-адрес панели /manage");
        }
        host = host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port < 0 && host.matches("[0-9.]+")) port = 9443;
        if (port == 0 || port > 65535) throw new IllegalArgumentException("Неверный порт панели");
        return new URI("https", null, host, port == 443 ? -1 : port, null, null, null).toString();
    }

    private SSLContext tls() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        X509TrustManager platform = null;
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) platform = (X509TrustManager) manager;
        }
        if (platform == null) throw new IOException("Системное хранилище сертификатов недоступно");
        final X509TrustManager defaults = platform;
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return defaults.getAcceptedIssuers(); }
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                defaults.checkClientTrusted(chain, authType);
            }
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try {
                    defaults.checkServerTrusted(chain, authType);
                } catch (CertificateException error) {
                    if (chain == null || chain.length == 0) throw error;
                    try {
                        chain[0].checkValidity();
                        String fingerprint = fingerprint(chain[0]);
                        if (fingerprint.equals(pin)) return;
                        untrusted = fingerprint;
                    } catch (Exception invalid) {
                        if (invalid instanceof CertificateException) throw (CertificateException) invalid;
                        throw new CertificateException(invalid);
                    }
                    throw error;
                }
            }
        }}, null);
        return context;
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder text = new StringBuilder();
        for (byte part : hash) {
            if (text.length() > 0) text.append(':');
            text.append(String.format(Locale.ROOT, "%02X", part & 255));
        }
        return text.toString();
    }

    JSONObject login(String password) throws Exception {
        session = "";
        JSONObject result = call("login", new JSONObject().put("password", password));
        csrf = result.getString("csrf");
        return result;
    }

    JSONObject call(String endpoint, JSONObject payload) throws Exception {
        if (!endpoint.matches("[a-z]+")) throw new IllegalArgumentException("Неверный запрос панели");
        untrusted = null;
        HttpsURLConnection connection = (HttpsURLConnection) new java.net.URL(origin + "/manage/api/" + endpoint).openConnection();
        try {
            connection.setSSLSocketFactory(tls().getSocketFactory());
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MTPoxy-Android/1.5");
            connection.setRequestProperty("Origin", origin);
            if (!session.isEmpty()) connection.setRequestProperty("Cookie", session);
            if (payload != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("X-CSRF-Token", csrf);
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int code = connection.getResponseCode();
            for (Map.Entry<String,List<String>> header : connection.getHeaderFields().entrySet()) {
                if ("Set-Cookie".equalsIgnoreCase(header.getKey())) {
                    for (String cookie : header.getValue()) {
                        if (cookie.startsWith("mtpoxy_session=")) session = cookie.split(";", 2)[0];
                    }
                }
            }
            InputStream source = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (source == null) throw new IOException("Панель вернула HTTP " + code);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = source) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 2 * 1024 * 1024) throw new IOException("Слишком большой ответ панели");
                }
            }
            JSONObject result = new JSONObject(bytes.toString("UTF-8"));
            if (code != 200) throw new HttpFailure(code, result.optString("error", "HTTP " + code));
            return result;
        } catch (IOException error) {
            if (untrusted != null) throw new CertificateApproval(untrusted);
            throw error;
        } finally {
            connection.disconnect();
        }
    }
}
