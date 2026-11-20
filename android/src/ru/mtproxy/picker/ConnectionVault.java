package ru.mtproxy.picker;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Connection credentials encrypted with a device-bound Android Keystore key. */
final class ConnectionVault {
    private static final String ALIAS = "mtpoxy.panel.connection";
    private final Context context;

    ConnectionVault(Context context) {
        this.context = context.getApplicationContext();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    JSONObject load() throws Exception {
        String saved = context.getSharedPreferences("panel", 0).getString("connection", "");
        if (saved.isEmpty()) return null;
        ByteBuffer bytes = ByteBuffer.wrap(Base64.decode(saved, Base64.NO_WRAP));
        int length = bytes.get() & 255;
        if (length != 12 || bytes.remaining() < length + 16) throw new IllegalStateException("Повторно введите настройки панели");
        byte[] iv = new byte[length];
        bytes.get(iv);
        byte[] encrypted = new byte[bytes.remaining()];
        bytes.get(encrypted);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
    }

    void save(String address, String password) throws Exception {
        JSONObject record = new JSONObject().put("address", address).put("password", password);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(record.toString().getBytes(StandardCharsets.UTF_8));
        ByteBuffer bytes = ByteBuffer.allocate(1 + iv.length + encrypted.length);
        bytes.put((byte) iv.length).put(iv).put(encrypted);
        boolean saved = context.getSharedPreferences("panel", 0).edit()
                .putString("connection", Base64.encodeToString(bytes.array(), Base64.NO_WRAP)).commit();
        if (!saved) throw new IllegalStateException("Не удалось сохранить подключение");
    }

    String pin(String address) {
        return context.getSharedPreferences("panel", 0).getString("certificate:" + address, "");
    }

    void pin(String address, String fingerprint) {
        context.getSharedPreferences("panel", 0).edit().putString("certificate:" + address, fingerprint).apply();
    }
}
