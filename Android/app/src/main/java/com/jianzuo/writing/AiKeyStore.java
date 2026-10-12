package com.jianzuo.writing;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** API credentials are separate from manuscript storage and all exported backups. */
final class AiKeyStore {
    private static final String ALIAS = "com.jianzuo.writing.deepseek.v1";
    private final SharedPreferences prefs;
    AiKeyStore(Context context) { prefs = context.getSharedPreferences("ai_credentials", Context.MODE_PRIVATE); }
    private SecretKey secret() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    boolean configured() { return prefs.contains("ciphertext"); }
    String read() throws Exception {
        if (!configured()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("ciphertext", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void save(String value, String model) throws Exception {
        String key = DeepSeekClient.key(value); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, secret());
        String ciphertext = Base64.encodeToString(cipher.doFinal(key.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString("ciphertext", ciphertext).putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).putString("model", model).commit()) throw new java.io.IOException("Credential storage");
    }
    String model() { return prefs.getString("model", DeepSeekClient.DEFAULT_MODEL); }
    void clear() throws Exception { if (!prefs.edit().clear().commit()) throw new java.io.IOException("Credential storage"); }
}
