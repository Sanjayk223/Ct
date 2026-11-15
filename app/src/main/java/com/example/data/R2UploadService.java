package com.example.data;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import com.example.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

public class R2UploadService {

    public interface UploadCallback {
        void onProgress(int percent);
        void onSuccess(String publicMediaUrl);
        void onError(String errorMessage);
    }

    private static final String REGION = "auto";
    private static final String SERVICE = "s3";

    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public R2UploadService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static boolean isConfigured() {
        return isValidValue(BuildConfig.R2_ACCOUNT_ID, "YOUR_CLOUDFLARE_R2_ACCOUNT_ID")
                && isValidValue(BuildConfig.R2_ACCESS_KEY_ID, "YOUR_R2_ACCESS_KEY_ID")
                && isValidValue(BuildConfig.R2_SECRET_ACCESS_KEY, "YOUR_R2_SECRET_ACCESS_KEY")
                && isValidValue(BuildConfig.R2_BUCKET_NAME, "YOUR_R2_BUCKET_NAME")
                && isValidValue(BuildConfig.R2_PUBLIC_URL, "https://pub-your-bucket.r2.dev");
    }

    private static boolean isValidValue(String val, String placeholder) {
        return val != null && !val.trim().isEmpty()
                && !val.trim().equals(placeholder)
                && !val.trim().startsWith("YOUR_");
    }

    public void uploadMedia(
            @NonNull Context context,
            @NonNull Uri sourceUri,
            @NonNull String folderPrefix,
            @NonNull String userId,
            @NonNull UploadCallback callback
    ) {
        if (!isConfigured()) {
            mainHandler.post(() -> callback.onError(
                    "Cloudflare R2 credentials are not configured. Add R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, "
                            + "R2_SECRET_ACCESS_KEY, R2_BUCKET_NAME, and R2_PUBLIC_URL in AI Studio Secrets."
            ));
            return;
        }

        executor.execute(() -> {
            File tempFile = null;
            try {
                ContentResolver resolver = context.getApplicationContext().getContentResolver();
                String mimeType = resolver.getType(sourceUri);
                if (mimeType == null || mimeType.trim().isEmpty()) {
                    mimeType = folderPrefix.contains("reel") ? "video/mp4" : "image/jpeg";
                }
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (ext == null || ext.isEmpty()) {
                    ext = mimeType.startsWith("video/") ? "mp4" : "jpg";
                }

                tempFile = File.createTempFile("r2_upload_", "." + ext, context.getCacheDir());
                try (InputStream in = resolver.openInputStream(sourceUri);
                     FileOutputStream out = new FileOutputStream(tempFile)) {
                    if (in == null) {
                        throw new IOException("Unable to read selected media file");
                    }
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                String payloadHash = sha256Hex(tempFile);
                String objectKey = folderPrefix + "/" + userId + "/" + UUID.randomUUID() + "." + ext;
                String accountId = BuildConfig.R2_ACCOUNT_ID.trim();
                String bucket = BuildConfig.R2_BUCKET_NAME.trim();
                String accessKey = BuildConfig.R2_ACCESS_KEY_ID.trim();
                String secretKey = BuildConfig.R2_SECRET_ACCESS_KEY.trim();
                String publicBase = BuildConfig.R2_PUBLIC_URL.trim().replaceAll("/+$", "");

                String host = accountId + ".r2.cloudflarestorage.com";
                String canonicalUri = "/" + bucket + "/" + objectKey;
                String endpointUrl = "https://" + host + canonicalUri;

                Date now = new Date();
                SimpleDateFormat amzFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
                amzFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String amzDate = amzFormat.format(now);

                SimpleDateFormat dateStampFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                dateStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String dateStamp = dateStampFormat.format(now);

                String canonicalHeaders = "content-type:" + mimeType + "\n"
                        + "host:" + host + "\n"
                        + "x-amz-content-sha256:" + payloadHash + "\n"
                        + "x-amz-date:" + amzDate + "\n";
                String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";

                String canonicalRequest = "PUT\n"
                        + canonicalUri + "\n"
                        + "\n"
                        + canonicalHeaders + "\n"
                        + signedHeaders + "\n"
                        + payloadHash;

                String credentialScope = dateStamp + "/" + REGION + "/" + SERVICE + "/aws4_request";
                String stringToSign = "AWS4-HMAC-SHA256\n"
                        + amzDate + "\n"
                        + credentialScope + "\n"
                        + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

                byte[] signingKey = getSignatureKey(secretKey, dateStamp, REGION, SERVICE);
                String signature = bytesToHex(hmacSha256(signingKey, stringToSign));

                String authHeader = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                        + ", SignedHeaders=" + signedHeaders
                        + ", Signature=" + signature;

                final File fileToUpload = tempFile;
                final long totalBytes = fileToUpload.length();
                final MediaType mediaType = MediaType.parse(mimeType);

                RequestBody body = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return mediaType;
                    }

                    @Override
                    public long contentLength() {
                        return totalBytes;
                    }

                    @Override
                    public void writeTo(@NonNull BufferedSink sink) throws IOException {
                        byte[] buf = new byte[16384];
                        long uploaded = 0;
                        int lastPct = -1;
                        try (FileInputStream fis = new FileInputStream(fileToUpload)) {
                            int r;
                            while ((r = fis.read(buf)) != -1) {
                                sink.write(buf, 0, r);
                                uploaded += r;
                                int pct = totalBytes > 0 ? (int) ((uploaded * 100L) / totalBytes) : 100;
                                if (pct != lastPct) {
                                    lastPct = pct;
                                    final int notifyPct = Math.min(100, pct);
                                    mainHandler.post(() -> callback.onProgress(notifyPct));
                                }
                            }
                        }
                    }
                };

                Request request = new Request.Builder()
                        .url(endpointUrl)
                        .put(body)
                        .header("Host", host)
                        .header("Content-Type", mimeType)
                        .header("x-amz-content-sha256", payloadHash)
                        .header("x-amz-date", amzDate)
                        .header("Authorization", authHeader)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String finalUrl = publicBase + "/" + objectKey;
                        mainHandler.post(() -> callback.onSuccess(finalUrl));
                    } else {
                        String errBody = response.body() != null ? response.body().string() : "";
                        String msg = "R2 upload failed (" + response.code() + "): "
                                + (errBody.isEmpty() ? response.message() : errBody);
                        mainHandler.post(() -> callback.onError(msg));
                    }
                }
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : "R2 upload failed";
                mainHandler.post(() -> callback.onError(msg));
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                }
            }
        });
    }

    private static String sha256Hex(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[16384];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read;
            while ((read = fis.read(buf)) != -1) {
                digest.update(buf, 0, read);
            }
        }
        return bytesToHex(digest.digest());
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(data));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02x", b));
        }
        return sb.toString();
    }
}
