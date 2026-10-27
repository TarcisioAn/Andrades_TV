package pt.andrades.tv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Atualizações a partir das releases do GitHub. Só fala com o GitHub, no máximo a cada 6 horas e só quando o
 * ecrã inicial abre: não há nenhum serviço a correr em segundo plano.
 */
final class Updater {
    private Updater() {}

    private static final long INTERVAL = 6 * 60 * 60 * 1000L;
    private static final String APK = "AndradesTV.apk";

    static final class Release {
        final int version;
        final String url, sha256;
        final long size;

        Release(int version, String url, long size, String sha256) {
            this.version = version;
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /** A release mais recente se for mais nova do que este app; null se não houver (ou ainda não for hora de ver). */
    static Release check(Context c) {
        SharedPreferences p = c.getSharedPreferences("updater", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        int known = p.getInt("version", 0);
        if (now - p.getLong("checked", 0) < INTERVAL) {
            return known > BuildInfo.VERSION_CODE
                    ? new Release(known, p.getString("url", null), p.getLong("size", -1), p.getString("sha", null))
                    : null;
        }
        try {
            JSONObject r = new JSONObject(get("https://api.github.com/repos/" + BuildInfo.REPO + "/releases/latest"));
            int version = Integer.parseInt(r.getString("tag_name").replaceAll("[^0-9]", ""));
            JSONArray assets = r.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (!APK.equals(a.getString("name"))) continue;
                String digest = a.optString("digest", "");
                String sha = digest.startsWith("sha256:") ? digest.substring(7) : null;
                Release rel = new Release(version, a.getString("browser_download_url"), a.getLong("size"), sha);
                p.edit().putLong("checked", now).putInt("version", version).putString("url", rel.url)
                        .putLong("size", rel.size).putString("sha", sha).apply();
                return version > BuildInfo.VERSION_CODE ? rel : null;
            }
        } catch (Exception ignored) {
            // Sem rede ou sem release: tenta-se de novo na próxima vez.
        }
        p.edit().putLong("checked", now).apply();
        return null;
    }

    /** O Android só deixa um app instalar APKs depois de o utilizador o autorizar uma vez nas definições. */
    static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    static Intent permissionIntent(Context c) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Descarrega, confirma o tamanho e o SHA-256 que o GitHub publica, e entrega o APK ao instalador do Android. */
    static void install(Context c, Release r) throws IOException {
        File apk = new File(c.getCacheDir(), APK);
        HttpURLConnection con = open(r.url);
        try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
            copy(in, out);
        } finally {
            con.disconnect();
        }
        if (r.size >= 0 && apk.length() != r.size) throw new IOException("tamanho errado");
        if (r.sha256 != null && !r.sha256.equalsIgnoreCase(sha256(apk))) throw new IOException("SHA-256 errado");

        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite(APK, 0, apk.length())) {
                copy(in, out);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            Intent done = new Intent(c, UpdateReceiver.class);
            s.commit(PendingIntent.getBroadcast(c, id, done, flags).getIntentSender());
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection con = open(url);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            con.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        if (!url.startsWith("https://")) throw new IOException("só https");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(30_000);
        con.setRequestProperty("User-Agent", "AndradesTV/" + BuildInfo.VERSION_CODE);
        return con;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    private static String sha256(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
