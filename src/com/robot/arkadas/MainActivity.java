package com.robot.arkadas;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.AudioFormat;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebResourceRequest;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    public static final String EXTRA_LISTEN = "listen";
    public static final String EXTRA_OPEN = "open";
    static final String DEFAULT_MODEL = "claude-haiku-5-5";

    private static final int REQ_MIC = 7;
    private static final int REQ_CAMERA = 8;
    private static final int REQ_PICK = 9;
    private static final int REQ_STORAGE = 10;
    private static final int REQ_CAM_LIVE = 12;
    private static final int REQ_CAM_PHOTO = 13;
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer recognizer;
    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean pageReady = false;
    private String openWhenReady = null;
    private boolean listenWhenReady = false;
    private boolean wantOverlay = false;
    private MediaPlayer player;
    private int speakSeq = 0;
    private boolean cloudErrorShown = false;
    private Uri photoUri;
    /** A photo (or cancel) that came back before the page finished loading, e.g. after Android restarted the app. */
    private String pendingPhoto = null;
    private boolean pendingCancel = false, cameraOpen = false;
    private PermissionRequest pendingCameraRequest;

    // hearing
    private final Hearing hearing = new Hearing();
    private int listenSeq = 0;
    private long cloudBackoffUntil = 0;
    private boolean pipeBad = false, usingPipe = false, gotReady = false, pttGoogle = false;
    private int pipeMisses = 0;
    private ParcelFileDescriptor pipeRead;
    private String lastLang = "tr-TR";
    private long lastSilence = 1500;
    private boolean lastPtt = false;
    private final ExecutorService prefetcher = Executors.newSingleThreadExecutor();

    // media panel (YouTube / Spotify)
    private FrameLayout root;
    private LinearLayout mediaBox;
    private WebView mediaWeb;
    private TextView mediaTitle;
    private Button mediaBack;
    private boolean mediaOpen = false, playerMode = false, playerLoaded = false;
    private String pendingMedia = null, lastMedia = null;
    private int mediaSeq = 0;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    static final String PLAYER_BASE = "https://gokhangeo.github.io/pamuk/";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("robot", MODE_PRIVATE);
        // The camera app may push us out of memory; keep where its photo goes so the result is not lost.
        if (state != null && state.getString("photoUri") != null) photoUri = Uri.parse(state.getString("photoUri"));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.parseColor("#141414"));
        getWindow().setNavigationBarColor(Color.parseColor("#141414"));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#141414"));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(true); // pages downloaded from GitHub live in internal storage
        web.setWebChromeClient(new WebChromeClient() {
            // The camera view asks for the live camera; grant it once Android's camera permission is given.
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsVideo = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsVideo = true;
                }
                if (!wantsVideo) {
                    request.deny();
                } else if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingCameraRequest = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM_LIVE);
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingPhoto != null) { js("onPhoto", pendingPhoto); pendingPhoto = null; }
                if (pendingCancel) { js("onPhotoCancel"); pendingCancel = false; }
                if (openWhenReady != null) {
                    js("openFromNotice", openWhenReady);
                    openWhenReady = null;
                }
                if (listenWhenReady) {
                    listenWhenReady = false;
                    js("startTalk");
                }
            }
        });
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "Android");
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#141414"));
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        web.loadUrl(WebAssets.url(this, "index.html"));
        checkForUpdate(false);
        handleIntent(getIntent());
        ensureOverlay(this);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    js("onTtsError", "Konuşma motoru başlatılamadı");
                    return;
                }
                int r = tts.setLanguage(new Locale("tr", "TR"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    js("onTtsError", "Telefonda Türkçe ses paketi yok. Ayarlar > Metin okuma bölümünden Türkçe'yi indirin.");
                }
                applyVoice();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) { js("onSpeakStart"); }

                    @Override
                    public void onDone(String id) { js("onSpeakEnd"); }

                    @Override
                    public void onError(String id) { js("onSpeakEnd"); }
                });
                ttsReady = true;
            }
        });

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String open = intent.getStringExtra(EXTRA_OPEN);
        if (open != null && !open.isEmpty()) {
            intent.removeExtra(EXTRA_OPEN);
            if (pageReady) js("openFromNotice", open);
            else openWhenReady = open;
            return;
        }
        if (!intent.getBooleanExtra(EXTRA_LISTEN, false)) return;
        intent.removeExtra(EXTRA_LISTEN);
        if (pageReady) js("startTalk");
        else listenWhenReady = true;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == REQ_MIC && !granted) {
            js("onListenError", "Mikrofon izni olmadan seni duyamam.");
        } else if (code == REQ_STORAGE || code == REQ_CAM_PHOTO) {
            if (granted) takePhoto();
            else js("onPhotoCancel");
        } else if (code == REQ_CAM_LIVE && pendingCameraRequest != null) {
            if (granted) pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pendingCameraRequest.deny();
            pendingCameraRequest = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaWeb != null) mediaWeb.onResume();
        RobotOverlayService.setHidden(true);
        if (wantOverlay) {
            wantOverlay = false;
            if (Settings.canDrawOverlays(this)) startOverlay();
            else js("onOverlayDenied");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoUri != null) out.putString("photoUri", photoUri.toString());
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Leaving the app (home button, switching apps) brings the floating robot back if it was stopped,
        // but not while the camera is open: it needs the memory and the robot would cover it.
        if (!cameraOpen) ensureOverlay(this);
    }

    /** Starts the floating robot when the parent turned it on and the permission is still granted. */
    static void ensureOverlay(android.content.Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("robot", MODE_PRIVATE);
        if (!p.getBoolean("overlay", false) || RobotOverlayService.isRunning() || !Settings.canDrawOverlays(ctx)) return;
        try {
            ctx.startForegroundService(new Intent(ctx, RobotOverlayService.class));
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        RobotOverlayService.setHidden(false);
        cancelListening();
        stopAllSpeech();
        if (mediaWeb != null) {
            mediaWeb.evaluateJavascript("window.mediaPause&&mediaPause()", null);
            mediaWeb.onPause();
        }
        js("onPaused");
    }

    @Override
    protected void onDestroy() {
        if (player != null) player.release();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        hearing.cancel();
        prefetcher.shutdownNow();
        if (mediaWeb != null) mediaWeb.destroy();
        super.onDestroy();
    }

    /** Calls window[fn](...args) in the page; every argument is passed as a JS string. */
    private void js(final String fn, final String... args) {
        main.post(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder("window.").append(fn).append("&&window.").append(fn).append("(");
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(JSONObject.quote(args[i]));
                }
                sb.append(")");
                web.evaluateJavascript(sb.toString(), null);
            }
        });
    }

    // ---------- voice ----------

    private void applyVoice() {
        tts.setSpeechRate(prefs.getFloat("rate", 1.0f));
        tts.setPitch(prefs.getFloat("pitch", 1.15f));
        String name = prefs.getString("voice", "");
        Voice chosen = null;
        Voice female = null;
        Set<Voice> voices = null;
        try { voices = tts.getVoices(); } catch (Exception ignored) { }
        if (voices == null) return;
        for (Voice v : voices) {
            if (!"tr".equals(v.getLocale().getLanguage())) continue;
            if (v.getName().equals(name)) chosen = v;
            if (female == null && v.getName().toLowerCase(Locale.ROOT).contains("female")) female = v;
        }
        if (chosen == null) chosen = female;
        if (chosen != null) tts.setVoice(chosen);
    }

    private String voicesJson() {
        JSONArray out = new JSONArray();
        if (!ttsReady) return out.toString();
        try {
            ArrayList<Voice> list = new ArrayList<Voice>();
            for (Voice v : tts.getVoices()) {
                if ("tr".equals(v.getLocale().getLanguage())) list.add(v);
            }
            java.util.Collections.sort(list, new java.util.Comparator<Voice>() {
                @Override
                public int compare(Voice a, Voice b) { return a.getName().compareTo(b.getName()); }
            });
            for (Voice v : list) {
                out.put(new JSONObject()
                        .put("name", v.getName())
                        .put("network", v.isNetworkConnectionRequired())
                        .put("female", v.getName().toLowerCase(Locale.ROOT).contains("female")));
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    // ---------- listening ----------

    private void startListening() {
        startListening("tr-TR", 1500, false);
    }

    private void startListening(String lang, long silenceMs) {
        startListening(lang, silenceMs, false);
    }

    /** The cloud ear the parent set up (Deepgram > Groq > Gemini), or null for the phone's own recognizer. */
    private Hearing.Engine cloudEngine() {
        String k = prefs.getString("deepgramKey", "").trim();
        if (!k.isEmpty()) return new Hearing.Engine("deepgram", k, prefs.getString("deepgramModel", "nova-3").trim());
        k = prefs.getString("groqKey", "").trim();
        if (!k.isEmpty()) return new Hearing.Engine("groq", k, prefs.getString("groqModel", "whisper-large-v3-turbo").trim());
        k = prefs.getString("geminiKey", "").trim();
        if (!k.isEmpty()) return new Hearing.Engine("gemini", k, prefs.getString("geminiModel", "").trim());
        return null;
    }

    private ArrayList<String> keyterms() {
        ArrayList<String> out = new ArrayList<String>();
        String[] base = {prefs.getString("botName", "Pamuk"), prefs.getString("kidName", ""), "Pamuk", "Spotify", "YouTube",
                "şarkı", "video", "masal", "bilmece", "şarkı aç", "video aç"};
        for (String b : base) {
            if (b == null) continue;
            b = b.trim();
            if (!b.isEmpty() && !out.contains(b)) out.add(b);
        }
        return out;
    }

    /** Stops whatever is listening (cloud recording, pipe feed, phone recognizer) without any callback. */
    private void cancelListening() {
        listenSeq++;
        hearing.cancel();
        if (recognizer != null) recognizer.cancel();
        closePipe();
    }

    private void closePipe() {
        if (pipeRead != null) {
            try { pipeRead.close(); } catch (Exception ignored) { }
            pipeRead = null;
        }
        usingPipe = false;
    }

    /** lang: "tr-TR" or "en-US"; silenceMs: how long a pause ends the turn; ptt: push-to-talk (ends on pttStop). */
    private void startListening(String lang, long silenceMs, boolean ptt) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            js("onListenError", "Önce mikrofon iznini vermelisin.");
            return;
        }
        stopAllSpeech();
        cancelListening();
        lastLang = lang; lastSilence = silenceMs; lastPtt = ptt;
        Hearing.Engine eng = cloudEngine();
        if (eng != null && System.currentTimeMillis() > cloudBackoffUntil) startCloud(eng, lang, silenceMs, ptt);
        else startGoogle(lang, silenceMs, ptt);
    }

    private void startCloud(Hearing.Engine eng, final String lang, final long silenceMs, final boolean ptt) {
        final int seq = listenSeq;
        boolean strict = "1".equals(prefs.getString("tvMode", "0"));
        hearing.listenCloud(eng, lang, silenceMs, ptt, strict, keyterms(), new Hearing.Cb() {
            @Override public void onStart() { if (seq == listenSeq) js("onListenStart"); }
            @Override public void onLevel(float level) { if (seq == listenSeq) js("onLevel", String.valueOf(level)); }
            @Override public void onBusy() { if (seq == listenSeq) js("onHearingBusy"); }
            @Override public void onHeard(String text) { if (seq == listenSeq) js("onHeard", text); }
            @Override public void onNothing() { if (seq == listenSeq) js("onNothingHeard"); }
            @Override
            public void onFail(final String message) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != listenSeq) return;
                        cloudBackoffUntil = System.currentTimeMillis() + 60000; // the phone's recognizer for a minute
                        js("onHearingNote", message + " Şimdilik telefonun ses tanımasıyla dinliyorum.");
                        if (ptt) js("onNothingHeard");
                        else startGoogle(lang, silenceMs, false);
                    }
                });
            }
        });
    }

    private void startGoogle(final String lang, final long silenceMs, final boolean ptt) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("onListenError", "Bu telefonda ses tanıma yok. Google uygulamasını yükleyin.");
            return;
        }
        final int seq = listenSeq;
        pttGoogle = ptt;
        gotReady = false;
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle b) { gotReady = true; js("onListenStart"); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { if (!usingPipe) js("onLevel", String.valueOf(rms)); }
                @Override public void onBufferReceived(byte[] buf) { }
                @Override public void onEndOfSpeech() { }
                @Override public void onEvent(int type, Bundle b) { }

                @Override
                public void onError(int error) {
                    boolean wasPipe = usingPipe;
                    hearing.cancel();
                    closePipe();
                    if (wasPipe && error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            && error != SpeechRecognizer.ERROR_NETWORK && error != SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                            && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        // The recognizer did not accept our cleaned audio: use its own microphone from now on.
                        pipeBad = true;
                        startGoogle(lastLang, lastSilence, lastPtt);
                        return;
                    }
                    if (wasPipe && ++pipeMisses >= 3) pipeBad = true; // repeatedly heard nothing through the pipe
                    if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                        js("onListenError", "İnternet bağlantısı yok gibi görünüyor.");
                    } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        js("onListenError", "Mikrofon izni gerekli.");
                    } else {
                        js("onNothingHeard");
                    }
                }

                @Override
                public void onResults(Bundle b) {
                    hearing.cancel();
                    closePipe();
                    pipeMisses = 0;
                    ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty() && list.get(0).trim().length() > 0) {
                        js("onHeard", list.get(0));
                    } else {
                        js("onNothingHeard");
                    }
                }

                @Override
                public void onPartialResults(Bundle b) {
                    ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) js("onPartial", list.get(0));
                }
            });
        }
        long silence = ptt ? 10000 : silenceMs;
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence);
        if (Build.VERSION.SDK_INT >= 33) {
            i.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, keyterms());
            // Feed the recognizer from our own recorder with the phone's call-quality noise suppression (TV noise).
            if (!pipeBad && !"0".equals(prefs.getString("micEnhance", "1"))) {
                ParcelFileDescriptor read = hearing.startPipe(new Hearing.Cb() {
                    @Override public void onStart() { }
                    @Override public void onLevel(float level) { js("onLevel", String.valueOf(level)); }
                    @Override public void onBusy() { }
                    @Override public void onHeard(String text) { }
                    @Override public void onNothing() { }
                    @Override public void onFail(String message) { }
                });
                if (read != null) {
                    pipeRead = read;
                    usingPipe = true;
                    i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read);
                    i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
                    i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
                    i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, Hearing.RATE);
                }
            }
        }
        try {
            recognizer.startListening(i);
        } catch (Exception e) {
            hearing.cancel();
            closePipe();
            js("onListenError", "Ses tanıma başlatılamadı.");
            return;
        }
        if (usingPipe) {
            // No "ready" within 3 s means the recognizer ignores outside audio: fall back to its own microphone.
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (seq != listenSeq || gotReady || !usingPipe) return;
                    pipeBad = true;
                    if (recognizer != null) recognizer.cancel();
                    hearing.cancel();
                    closePipe();
                    startGoogle(lang, silenceMs, ptt);
                }
            }, 3000);
        }
    }

    /** Push-to-talk released: the cloud recording is sent, or the phone recognizer finishes with what it heard. */
    private void pttRelease() {
        if (hearing.active() && !usingPipe) hearing.finish();
        else if (recognizer != null && pttGoogle) recognizer.stopListening();
    }

    // ---------- homework photo ----------

    private void takePhoto() {
        // Declaring CAMERA in the manifest means the camera app may only be launched once it is granted.
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM_PHOTO);
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        launchCamera();
    }

    private void launchCamera() {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "odev_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RobotArkadas");
            photoUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            cameraOpen = true;
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            deletePhoto();
            // No camera app: let the parent pick an existing picture instead.
            try {
                Intent p = new Intent(Intent.ACTION_GET_CONTENT);
                p.setType("image/*");
                startActivityForResult(p, REQ_PICK);
            } catch (Exception e2) {
                js("onPhotoError", "Kamera açılamadı.");
            }
        }
    }

    private void deletePhoto() {
        if (photoUri == null) return;
        try { getContentResolver().delete(photoUri, null, null); } catch (Exception ignored) { }
        photoUri = null;
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_CAMERA && req != REQ_PICK) return;
        cameraOpen = false;
        final Uri uri = req == REQ_CAMERA ? photoUri : (data != null ? data.getData() : null);
        if (result != RESULT_OK || uri == null) {
            if (req == REQ_CAMERA) deletePhoto();
            if (pageReady) js("onPhotoCancel"); else pendingCancel = true;
            return;
        }
        final boolean fromCamera = req == REQ_CAMERA;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String b64 = encodePhoto(uri);
                    main.post(new Runnable() {
                        @Override public void run() { if (pageReady) js("onPhoto", b64); else pendingPhoto = b64; }
                    });
                } catch (Throwable e) { // OutOfMemoryError on big photos must not close the app
                    js("onPhotoError", "Fotoğrafı okuyamadım. Bir daha dener misin?");
                } finally {
                    if (fromCamera) main.post(new Runnable() {
                        @Override public void run() { deletePhoto(); }
                    });
                }
            }
        }).start();
    }

    /** Loads, rotates and shrinks the photo to at most 1280px, returned as base64 JPEG. */
    private String encodePhoto(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = getContentResolver().openInputStream(uri);
        BitmapFactory.decodeStream(in, null, o);
        in.close();
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1280) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        in = getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(in, null, o2);
        in.close();

        int rotate = 0;
        try {
            in = getContentResolver().openInputStream(uri);
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            in.close();
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
        } catch (Exception ignored) { }

        float scale = Math.min(1f, 1280f / Math.max(bmp.getWidth(), bmp.getHeight()));
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postRotate(rotate);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, 82, bo);
        return Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
    }

    // ---------- floating robot ----------

    private void startOverlay() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
        prefs.edit().putBoolean("overlay", true).apply();
        startForegroundService(new Intent(this, RobotOverlayService.class));
        RobotOverlayService.setHidden(true);
    }

    // ---------- speech output ----------

    private void stopAllSpeech() {
        speakSeq++;
        if (tts != null) tts.stop();
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private void speakPhone(String text, boolean english, float rateMul) {
        if (!ttsReady) {
            js("onSpeakEnd");
            return;
        }
        if (english) {
            tts.setLanguage(Locale.US);
            tts.setSpeechRate(prefs.getFloat("rate", 1.0f) * rateMul);
            tts.setPitch(prefs.getFloat("pitch", 1.15f));
        } else {
            tts.setLanguage(new Locale("tr", "TR"));
            applyVoice();
            tts.setSpeechRate(prefs.getFloat("rate", 1.0f) * rateMul);
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis());
    }

    static final String DEFAULT_TTS = "edge";

    private String ttsProvider() {
        return prefs.getString("ttsProvider", DEFAULT_TTS);
    }

    /**
     * Speaks with the chosen voice: Microsoft Edge neural (free, default), ElevenLabs or Google.
     * A paid voice that fails falls back to the free Edge voice, and Edge falls back to the phone's own voice.
     */
    private void speak(final String text, final boolean english, final float rateMul) {
        stopAllSpeech();
        final String provider = ttsProvider();
        if ("phone".equals(provider)) {
            speakPhone(text, english, rateMul);
            return;
        }
        final int seq = speakSeq;
        new Thread(new Runnable() {
            @Override
            public void run() {
                File mp3 = null;
                String error = null;
                try {
                    mp3 = "edge".equals(provider) ? edgeAudio(text, english, rateMul) : cloudAudio(provider, text, english, rateMul);
                } catch (Exception e) {
                    error = e.getMessage() == null ? "Bulut sesi alınamadı" : e.getMessage();
                    if (!"edge".equals(provider)) {
                        try { mp3 = edgeAudio(text, english, rateMul); } catch (Exception ignored) { }
                    }
                }
                final File ready = mp3;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != speakSeq) return;
                        if (err != null && !cloudErrorShown) {
                            cloudErrorShown = true;
                            js("onCloudTtsError", "edge".equals(provider) ? "Doğal ses şu an alınamadı (internet?)." : err, ready != null ? "edge" : "phone");
                        }
                        if (ready != null) playFile(ready, seq);
                        else speakPhone(text, english, rateMul);
                    }
                });
            }
        }).start();
    }

    /** Synthesizes ahead the sentence that will be spoken next, so the voice does not pause between sentences. */
    private void prefetch(final String text, final boolean english, final float rateMul) {
        if (!"edge".equals(ttsProvider())) return; // only the free voice; paid ones would spend credits on skipped lines
        prefetcher.execute(new Runnable() {
            @Override
            public void run() {
                try { edgeAudio(text, english, rateMul); } catch (Exception ignored) { }
            }
        });
    }

    /** Free Microsoft Edge neural voice (Emel or Ahmet) as an MP3 file, cached. */
    private File edgeAudio(String text, boolean english, float rateMul) throws Exception {
        String voice = prefs.getString("edgeVoice", "tr-TR-EmelNeural");
        if (english) voice = voice.contains("Ahmet") ? "en-US-GuyNeural" : "en-US-JennyNeural";
        float rate = parseFloatOr(prefs.getString("edgeRate", "0"), 0f);
        float pitch = parseFloatOr(prefs.getString("edgePitch", "0"), 0f);
        int pct = Math.round(((1f + rate / 100f) * rateMul - 1f) * 100f);
        pct = Math.max(-50, Math.min(100, pct));
        int hz = Math.max(-40, Math.min(40, Math.round(pitch)));
        String r = (pct >= 0 ? "+" : "") + pct + "%", p = (hz >= 0 ? "+" : "") + hz + "Hz";
        File dir = new File(getCacheDir(), "voice");
        dir.mkdirs();
        File f = new File(dir, "e" + Integer.toHexString((voice + r + p + text).hashCode()) + ".mp3");
        if (f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f; }
        byte[] audio = EdgeTts.synth(text, voice, r, p);
        File tmp = new File(dir, f.getName() + "." + Thread.currentThread().getId() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(audio);
        fo.close();
        tmp.renameTo(f);
        trimCache(dir);
        return f;
    }

    private void playFile(File f, final int seq) {
        try {
            player = new MediaPlayer();
            player.setDataSource(f.getAbsolutePath());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) { if (seq == speakSeq) js("onSpeakEnd"); }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (seq == speakSeq) js("onSpeakEnd");
                    return true;
                }
            });
            player.prepare();
            player.start();
            js("onSpeakStart");
        } catch (Exception e) {
            js("onSpeakEnd");
        }
    }

    /** Returns an MP3 for the text, reusing a cached copy so repeated phrases cost nothing. */
    private File cloudAudio(String provider, String text, boolean english, float rateMul) throws Exception {
        String voice = prefs.getString("cloudVoice", "");
        float rate = prefs.getFloat("rate", 1.0f) * rateMul, pitch = prefs.getFloat("pitch", 1.15f);
        File dir = new File(getCacheDir(), "voice");
        dir.mkdirs();
        File f = new File(dir, Integer.toHexString((provider + voice + rate + pitch + english + text).hashCode()) + ".mp3");
        if (f.length() > 0) return f;

        byte[] audio;
        if ("eleven".equals(provider)) {
            String key = prefs.getString("elevenKey", "").trim();
            if (key.isEmpty() || voice.isEmpty()) throw new Exception("ElevenLabs anahtarı veya sesi seçilmemiş.");
            JSONObject body = new JSONObject()
                    .put("text", text)
                    .put("model_id", "eleven_multilingual_v2")
                    .put("voice_settings", new JSONObject()
                            .put("stability", 0.45).put("similarity_boost", 0.8).put("style", 0.35)
                            .put("speed", Math.max(0.7, Math.min(1.2, rate))));
            audio = http("https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128",
                    "xi-api-key", key, body.toString());
        } else {
            String key = prefs.getString("googleKey", "").trim();
            if (key.isEmpty()) throw new Exception("Google anahtarı girilmemiş.");
            if (voice.isEmpty()) voice = "tr-TR-Chirp3-HD-Aoede";
            String lang = "tr-TR";
            if (english) {
                // Chirp 3 HD and most Google voices exist under the same name in en-US.
                lang = "en-US";
                voice = voice.replace("tr-TR", "en-US");
            }
            JSONObject cfg = new JSONObject().put("audioEncoding", "MP3").put("speakingRate", rate);
            if (!voice.contains("Chirp")) cfg.put("pitch", Math.max(-20, Math.min(20, (pitch - 1f) * 10f)));
            JSONObject body = new JSONObject()
                    .put("input", new JSONObject().put("text", text))
                    .put("voice", new JSONObject().put("languageCode", lang).put("name", voice))
                    .put("audioConfig", cfg);
            byte[] res = http("https://texttospeech.googleapis.com/v1/text:synthesize?key=" + key, null, null, body.toString());
            audio = Base64.decode(new JSONObject(new String(res, "UTF-8")).getString("audioContent"), Base64.DEFAULT);
        }
        File tmp = new File(dir, f.getName() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(audio);
        fo.close();
        tmp.renameTo(f);
        trimCache(dir);
        return f;
    }

    private static void trimCache(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 200) return;
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
        });
        for (int i = 0; i < files.length - 150; i++) files[i].delete();
    }

    /** Small HTTP helper for the voice services: POST when body is set, GET otherwise. */
    private static byte[] http(String url, String headerName, String headerValue, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        if (headerName != null) c.setRequestProperty(headerName, headerValue);
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("content-type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
        }
        c.disconnect();
        if (code == 401 || code == 403) throw new Exception("Ses servisi anahtarı geçersiz veya yetkisiz.");
        if (code == 429) throw new Exception("Ses servisinin kotası doldu.");
        if (code >= 400) throw new Exception("Ses servisi hatası (" + code + ")");
        return bo.toByteArray();
    }

    /** Lists the voices a cloud provider offers, as [{id, label}] JSON. */
    private String cloudVoices(String provider) throws Exception {
        JSONArray out = new JSONArray();
        if ("eleven".equals(provider)) {
            String key = prefs.getString("elevenKey", "").trim();
            JSONObject res = new JSONObject(new String(http("https://api.elevenlabs.io/v1/voices", "xi-api-key", key, null), "UTF-8"));
            JSONArray voices = res.getJSONArray("voices");
            ArrayList<JSONObject> female = new ArrayList<JSONObject>(), other = new ArrayList<JSONObject>();
            for (int i = 0; i < voices.length(); i++) {
                JSONObject v = voices.getJSONObject(i);
                JSONObject labels = v.optJSONObject("labels");
                String gender = labels == null ? "" : labels.optString("gender");
                String age = labels == null ? "" : labels.optString("age");
                String label = v.optString("name")
                        + ("female".equals(gender) ? " · kadın" : "male".equals(gender) ? " · erkek" : "")
                        + (age.contains("young") ? " · genç" : "");
                JSONObject o = new JSONObject().put("id", v.optString("voice_id")).put("label", label);
                if ("female".equals(gender)) female.add(o); else other.add(o);
            }
            for (JSONObject o : female) out.put(o);
            for (JSONObject o : other) out.put(o);
        } else {
            String key = prefs.getString("googleKey", "").trim();
            JSONObject res = new JSONObject(new String(http(
                    "https://texttospeech.googleapis.com/v1/voices?languageCode=tr-TR&key=" + key, null, null, null), "UTF-8"));
            JSONArray voices = res.optJSONArray("voices");
            String[] tiers = {"Chirp3-HD", "Chirp-HD", "Neural2", "Wavenet", "Standard"};
            String[] tierNames = {"en doğal", "çok doğal", "doğal", "iyi", "basit"};
            for (String gender : new String[]{"FEMALE", "MALE"}) {
                for (int t = 0; t < tiers.length; t++) {
                    for (int i = 0; voices != null && i < voices.length(); i++) {
                        JSONObject v = voices.getJSONObject(i);
                        String name = v.optString("name");
                        if (!name.startsWith("tr-TR") || !name.contains("-" + tiers[t] + "-") || !gender.equals(v.optString("ssmlGender"))) continue;
                        String shortName = name.substring(name.lastIndexOf('-') + 1);
                        out.put(new JSONObject().put("id", name).put("label",
                                shortName + " · " + ("FEMALE".equals(gender) ? "kadın" : "erkek") + " · " + tierNames[t]));
                    }
                }
            }
        }
        return out.toString();
    }

    // ---------- media panel: YouTube and Spotify inside the app ----------

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void ensureMediaPanel() {
        if (mediaBox != null) return;
        mediaBox = new LinearLayout(this);
        mediaBox.setOrientation(LinearLayout.VERTICAL);
        mediaBox.setBackgroundColor(Color.parseColor("#0d1117"));
        mediaBox.setVisibility(View.GONE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        mediaBack = new Button(this);
        mediaBack.setText("◀ Geri");
        mediaBack.setAllCaps(false);
        mediaBack.setVisibility(View.GONE);
        mediaBack.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPlayer(); }
        });
        mediaTitle = new TextView(this);
        mediaTitle.setTextColor(Color.WHITE);
        mediaTitle.setTextSize(15);
        mediaTitle.setSingleLine(true);
        mediaTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mediaTitle.setPadding(dp(8), 0, dp(8), 0);
        Button close = new Button(this);
        close.setText("✕ Kapat");
        close.setAllCaps(false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeMedia(); }
        });
        bar.addView(mediaBack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.addView(mediaTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mediaBox.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mediaWeb = new WebView(this);
        mediaWeb.setBackgroundColor(Color.parseColor("#0d1117"));
        WebSettings ws = mediaWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        // The Spotify player sits in an iframe of our page; its login cookie is a third-party cookie there.
        CookieManager.getInstance().setAcceptThirdPartyCookies(mediaWeb, true);
        mediaWeb.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) { // Spotify full tracks use DRM (Widevine)
                        request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                        return;
                    }
                }
                request.deny();
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = cb;
                root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() { hideCustomView(); }
        });
        mediaWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if ("spotify".equals(scheme) || "intent".equals(scheme)) { openSpotifyApp(u.toString()); return true; }
                if (playerMode) return req.isForMainFrame(); // the child stays on the player; links out of it are blocked
                String host = u.getHost() == null ? "" : u.getHost();
                String[] ok = {"spotify.com", "spotifycdn.com", "scdn.co", "google.com", "gstatic.com", "recaptcha.net", "apple.com", "facebook.com", "hcaptcha.com"};
                for (String o : ok) if (host.equals(o) || host.endsWith("." + o)) return false;
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (playerMode && url != null && url.startsWith(PLAYER_BASE)) {
                    playerLoaded = true;
                    if (pendingMedia != null) { loadIntoPlayer(pendingMedia); pendingMedia = null; }
                }
            }
        });
        mediaWeb.addJavascriptInterface(new MediaBridge(), "Media");
        mediaBox.addView(mediaWeb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        int h = Math.round(getResources().getDisplayMetrics().heightPixels * 0.56f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h, Gravity.TOP);
        root.addView(mediaBox, lp);
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    private String playerHtml() {
        try {
            File f = new File(WebAssets.dir(this), "player.html");
            InputStream in = (WebAssets.downloadedVersion(this) > WebAssets.bundledVersion(this) && f.exists())
                    ? new java.io.FileInputStream(f) : getAssets().open("player.html");
            return readAll(in);
        } catch (Exception e) {
            return "<html><body style='background:#0d1117;color:#fff;font-family:sans-serif'>Oynatıcı yüklenemedi.</body></html>";
        }
    }

    /** Shows the player page (loading it if needed) and plays `json` there. */
    private void showPlayer() {
        mediaBack.setVisibility(View.GONE);
        if (!playerMode || !playerLoaded) {
            playerMode = true;
            playerLoaded = false;
            if (pendingMedia == null) pendingMedia = lastMedia;
            mediaWeb.loadDataWithBaseURL(PLAYER_BASE, playerHtml(), "text/html", "UTF-8", null);
        }
    }

    private void loadIntoPlayer(String json) {
        mediaWeb.evaluateJavascript("window.loadMedia&&loadMedia(" + json + ")", null);
    }

    /** kind: "youtube" or "spotify". Searches off the main thread, then plays the first result in the panel. */
    private void openMedia(final String kind, final String query) {
        ensureMediaPanel();
        cancelListening();
        stopAllSpeech();
        final int seq = ++mediaSeq;
        mediaBox.setVisibility(View.VISIBLE);
        mediaTitle.setText(("spotify".equals(kind) ? "🎵 " : "▶️ ") + query + " aranıyor…");
        if (!mediaOpen) { mediaOpen = true; }
        js("onMediaOpen", kind, query);
        showPlayer();
        new Thread(new Runnable() {
            @Override
            public void run() {
                JSONArray items;
                try {
                    items = "spotify".equals(kind)
                            ? MediaSearch.spotify(query, prefs.getString("spotifyClientId", ""), prefs.getString("spotifyClientSecret", ""))
                            : MediaSearch.youtube(query);
                } catch (Exception e) {
                    items = new JSONArray();
                }
                String json;
                try {
                    json = new JSONObject().put("kind", kind).put("q", query).put("items", items).toString();
                } catch (Exception e) {
                    return;
                }
                final String j = json;
                final int n = items.length();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != mediaSeq || !mediaOpen) return;
                        lastMedia = j;
                        mediaTitle.setText(("spotify".equals(kind) ? "🎵 " : "▶️ ") + query);
                        if (n == 0) js("onMediaFailed", "notfound");
                        if (playerMode && playerLoaded) loadIntoPlayer(j);
                        else { pendingMedia = j; showPlayer(); }
                    }
                });
            }
        }).start();
    }

    private void closeMedia() {
        if (mediaBox == null || !mediaOpen) return;
        hideCustomView();
        mediaSeq++;
        mediaOpen = false;
        mediaWeb.evaluateJavascript("window.mediaStop&&mediaStop()", null);
        mediaWeb.loadUrl("about:blank");
        playerMode = false;
        playerLoaded = false;
        pendingMedia = null;
        mediaBox.setVisibility(View.GONE);
        js("onMediaClose");
    }

    private void openSpotifyApp(String uri) {
        try {
            Intent i;
            if (uri.startsWith("intent:")) i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
            else i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            js("onHearingNote", "Spotify uygulaması bu telefonda yüklü değil gibi görünüyor.");
        }
    }

    private void browseSpotify(String url) {
        ensureMediaPanel();
        if (!mediaOpen) { mediaOpen = true; mediaBox.setVisibility(View.VISIBLE); js("onMediaOpen", "spotify", ""); }
        mediaWeb.evaluateJavascript("window.mediaPause&&mediaPause()", null);
        playerMode = false;
        playerLoaded = false;
        mediaBack.setVisibility(View.VISIBLE);
        mediaTitle.setText("Spotify");
        mediaWeb.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) { hideCustomView(); return; }
        if (mediaOpen && !playerMode) { showPlayer(); return; }
        if (mediaOpen) { closeMedia(); return; }
        super.onBackPressed();
    }

    /** Called by player.html as window.Media.* */
    public class MediaBridge {
        @JavascriptInterface
        public void close() {
            main.post(new Runnable() { @Override public void run() { closeMedia(); } });
        }

        @JavascriptInterface
        public void ended() { js("onMediaEnded"); }

        @JavascriptInterface
        public void playing(final String title) {
            main.post(new Runnable() {
                @Override public void run() { if (mediaTitle != null && title != null && !title.isEmpty()) mediaTitle.setText(title); }
            });
        }

        @JavascriptInterface
        public void failed(String why) { js("onMediaFailed", why); }

        @JavascriptInterface
        public void openSpotifyApp(final String uri) {
            main.post(new Runnable() { @Override public void run() { MainActivity.this.openSpotifyApp(uri); } });
        }

        @JavascriptInterface
        public void spotifyLogin() {
            main.post(new Runnable() {
                @Override public void run() { browseSpotify("https://accounts.spotify.com/tr/login?continue=https%3A%2F%2Fopen.spotify.com%2F"); }
            });
        }

        @JavascriptInterface
        public void spotifyWeb(final String q) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    String url = "https://open.spotify.com/search/";
                    try { url += java.net.URLEncoder.encode(q == null ? "" : q, "UTF-8").replace("+", "%20"); } catch (Exception ignored) { }
                    browseSpotify(url);
                }
            });
        }
    }

    // ---------- updates from GitHub ----------

    /** Fetches newer web files in the background; the page decides when to reload (when it is idle). */
    private void checkForUpdate(final boolean manual) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int v = WebAssets.update(MainActivity.this);
                    if (v > 0) {
                        RobotOverlayService.reloadPage();
                        js("onUpdateReady", String.valueOf(v));
                    } else if (manual) {
                        js("onUpdateResult", "En güncel sürümdesiniz (web sürümü " + WebAssets.currentVersion(MainActivity.this) + ").");
                    }
                } catch (Exception e) {
                    if (manual) js("onUpdateResult", "Güncelleme kontrol edilemedi: " + e.getMessage());
                }
            }
        }).start();
    }

    // ---------- Claude ----------

    /** Sends the conversation to the Claude Messages API and returns the reply text. */
    private String callClaude(String system, JSONArray messages, String effort) throws Exception {
        String key = prefs.getString("apiKey", "").trim();
        if (key.isEmpty()) throw new UserError("Önce ayarlardan Claude API anahtarını girin.");
        String model = prefs.getString("model", DEFAULT_MODEL);

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 4096);
        body.put("system", system);
        body.put("messages", messages);
        body.put("output_config", new JSONObject().put("effort", effort));
        boolean fallback = model.startsWith("claude-opus") || model.startsWith("claude-sonnet");
        if (fallback) body.put("fallbacks", "default");

        HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("content-type", "application/json");
        c.setRequestProperty("x-api-key", key);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        if (fallback) c.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.close();

        int code = c.getResponseCode();
        String text = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();

        if (code == 401) throw new UserError("API anahtarı geçersiz. Ayarlardan kontrol edin.");
        if (code == 429) throw new UserError("Çok hızlı konuştuk, biraz dinlenelim. Birazdan tekrar dene.");
        if (code == 529 || code >= 500) throw new UserError("Beynim şu an çok meşgul, birazdan tekrar dene.");
        if (code >= 400) {
            String msg = text;
            try { msg = new JSONObject(text).getJSONObject("error").getString("message"); } catch (Exception ignored) { }
            if (msg.toLowerCase(Locale.ROOT).contains("credit")) throw new UserError("API hesabında kredi bitmiş görünüyor.");
            throw new UserError("Hata (" + code + "): " + msg);
        }

        JSONObject res = new JSONObject(text);
        if ("refusal".equals(res.optString("stop_reason"))) {
            return "Bu konuyu konuşamam ama başka bir şey sorabilirsin. İstersen bir bilmece sorayım mı?";
        }
        JSONArray content = res.getJSONArray("content");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) out.append(block.optString("text"));
        }
        return out.toString().trim();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private static float parseFloatOr(String s, float def) {
        try { return Float.parseFloat(s); } catch (Exception e) { return def; }
    }

    private static class UserError extends Exception {
        UserError(String m) { super(m); }
    }

    /** Methods callable from index.html as window.Android.* */
    public class Bridge {
        @JavascriptInterface
        public void listen() {
            main.post(new Runnable() {
                @Override public void run() { startListening(); }
            });
        }

        @JavascriptInterface
        public void stopListening() {
            main.post(new Runnable() {
                @Override public void run() { cancelListening(); }
            });
        }

        @JavascriptInterface
        public void speak(final String text) {
            speakWith(text, "tr", "1");
        }

        @JavascriptInterface
        public void speakWith(final String text, final String lang, final String rateMul) {
            final float mul = parseFloatOr(rateMul, 1f);
            main.post(new Runnable() {
                @Override public void run() { MainActivity.this.speak(text, "en".equals(lang), mul); }
            });
        }

        @JavascriptInterface
        public void listenWith(final String lang, final String silenceMs) {
            final long silence = (long) parseFloatOr(silenceMs, 1500);
            main.post(new Runnable() {
                @Override public void run() { startListening(lang, silence); }
            });
        }

        @JavascriptInterface
        public void setLook(String json) {
            prefs.edit().putString("look", json).apply();
            RobotOverlayService.refreshLook();
        }

        @JavascriptInterface
        public void stopSpeaking() {
            main.post(new Runnable() {
                @Override public void run() { stopAllSpeech(); }
            });
        }

        @JavascriptInterface
        public void loadCloudVoices(final String provider) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        js("onCloudVoices", cloudVoices(provider));
                    } catch (Exception e) {
                        js("onCloudVoicesError", e.getMessage() == null ? "Sesler alınamadı" : e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public void ask(final String system, final String messagesJson, final String effort) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        js("onAnswer", callClaude(system, new JSONArray(messagesJson), effort));
                    } catch (UserError e) {
                        js("onAskError", e.getMessage());
                    } catch (java.net.UnknownHostException e) {
                        js("onAskError", "İnternet bağlantısı yok gibi görünüyor.");
                    } catch (java.net.SocketTimeoutException e) {
                        js("onAskError", "Cevap çok gecikti, tekrar dener misin?");
                    } catch (Exception e) {
                        js("onAskError", "Bir sorun oldu: " + e.getMessage());
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public String get(String key, String def) {
            return prefs.getString(key, def);
        }

        @JavascriptInterface
        public void set(String key, String value) {
            prefs.edit().putString(key, value).apply();
            cloudErrorShown = false;
            if (key.endsWith("Key")) { cloudBackoffUntil = 0; pipeBad = false; }
            if ("botName".equals(key)) RobotWidget.refresh(MainActivity.this);
        }

        @JavascriptInterface
        public void setVoice(String rate, String pitch, String name) {
            try {
                prefs.edit().putFloat("rate", Float.parseFloat(rate)).putFloat("pitch", Float.parseFloat(pitch))
                        .putString("voice", name).apply();
            } catch (NumberFormatException ignored) { }
        }

        @JavascriptInterface
        public String getVoice() {
            return prefs.getFloat("rate", 1.0f) + "," + prefs.getFloat("pitch", 1.15f) + "," + prefs.getString("voice", "");
        }

        @JavascriptInterface
        public String voices() {
            return voicesJson();
        }

        @JavascriptInterface
        public void openTtsSettings() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
                    } catch (Exception ignored) { }
                }
            });
        }

        @JavascriptInterface
        public void takePhoto() {
            main.post(new Runnable() {
                @Override public void run() { MainActivity.this.takePhoto(); }
            });
        }

        @JavascriptInterface
        public void reloadApp() {
            main.post(new Runnable() {
                @Override public void run() { web.loadUrl(WebAssets.url(MainActivity.this, "index.html")); }
            });
        }

        @JavascriptInterface
        public void checkUpdate() {
            checkForUpdate(true);
        }

        @JavascriptInterface
        public boolean cameraSupported() {
            return true;
        }

        /** Saves a robot photo from the camera view into the gallery (Pictures/RobotArkadas). */
        @JavascriptInterface
        public void savePhoto(final String base64) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE + 100);
                            js("onPhotoSaved", "Galeriye kaydetmek için depolama izni gerekiyor; izin verip tekrar dene.");
                            return;
                        }
                        byte[] jpg = Base64.decode(base64, Base64.DEFAULT);
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "robot_" + System.currentTimeMillis() + ".jpg");
                        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RobotArkadas");
                        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        os.write(jpg);
                        os.close();
                        js("onPhotoSaved", "Fotoğraf galeriye kaydedildi! 📸");
                    } catch (Exception e) {
                        js("onPhotoSaved", "Fotoğraf kaydedilemedi.");
                    }
                }
            }).start();
        }

        @JavascriptInterface
        public String versionInfo() {
            return "Uygulama " + WebAssets.appVersion(MainActivity.this) + " · web " + WebAssets.currentVersion(MainActivity.this);
        }

        @JavascriptInterface
        public void openBatterySettings() {
            main.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); } catch (Exception ignored) { }
                    }
                }
            });
        }

        /** Plans reminders that appear as notifications even when the app is closed (exams etc.). */
        @JavascriptInterface
        public void scheduleNotices(String json) {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
            }
            Notices.schedule(MainActivity.this, json);
        }

        // ---------- v3.0 (app 10) ----------

        @JavascriptInterface
        public void copyText(final String text) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Pamuk yedeği", text));
                }
            });
        }

        @JavascriptInterface
        public String pasteText() {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) return "";
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                return t == null ? "" : t.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void setFloat(String key, String value) {
            try { prefs.edit().putFloat(key, Float.parseFloat(value)).apply(); } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public String hearingEngine() {
            Hearing.Engine e = cloudEngine();
            return e == null ? "google" : e.kind;
        }

        @JavascriptInterface
        public void pttStart(final String lang) {
            main.post(new Runnable() {
                @Override public void run() { startListening(lang == null || lang.isEmpty() ? "tr-TR" : lang, 1500, true); }
            });
        }

        @JavascriptInterface
        public void pttStop() {
            main.post(new Runnable() { @Override public void run() { pttRelease(); } });
        }

        @JavascriptInterface
        public void prefetchSpeech(final String text, final String lang, final String rateMul) {
            prefetch(text, "en".equals(lang), parseFloatOr(rateMul, 1f));
        }

        @JavascriptInterface
        public void openYoutube(final String q) {
            main.post(new Runnable() { @Override public void run() { openMedia("youtube", q); } });
        }

        @JavascriptInterface
        public void openSpotify(final String q) {
            main.post(new Runnable() { @Override public void run() { openMedia("spotify", q); } });
        }

        @JavascriptInterface
        public void closeMedia() {
            main.post(new Runnable() { @Override public void run() { MainActivity.this.closeMedia(); } });
        }

        @JavascriptInterface
        public boolean mediaIsOpen() { return mediaOpen; }

        @JavascriptInterface
        public void mediaPause() {
            main.post(new Runnable() {
                @Override public void run() { if (mediaWeb != null && mediaOpen) mediaWeb.evaluateJavascript("window.mediaPause&&mediaPause()", null); }
            });
        }

        @JavascriptInterface
        public void mediaResume() {
            main.post(new Runnable() {
                @Override public void run() { if (mediaWeb != null && mediaOpen && playerMode) mediaWeb.evaluateJavascript("window.mediaResume&&mediaResume()", null); }
            });
        }

        @JavascriptInterface
        public void spotifyLogin() {
            main.post(new Runnable() {
                @Override public void run() { browseSpotify("https://accounts.spotify.com/tr/login?continue=https%3A%2F%2Fopen.spotify.com%2F"); }
            });
        }

        @JavascriptInterface
        public int appVersion() { return WebAssets.appVersion(MainActivity.this); }

        @JavascriptInterface
        public boolean overlayOn() {
            return prefs.getBoolean("overlay", false);
        }

        @JavascriptInterface
        public void setOverlay(final boolean on) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (!on) {
                        prefs.edit().putBoolean("overlay", false).apply();
                        stopService(new Intent(MainActivity.this, RobotOverlayService.class));
                    } else if (Settings.canDrawOverlays(MainActivity.this)) {
                        startOverlay();
                    } else {
                        wantOverlay = true;
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    }
                }
            });
        }
    }
}
