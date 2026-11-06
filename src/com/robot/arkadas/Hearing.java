package com.robot.arkadas;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/**
 * Microphone capture with the phone's voice-call noise suppression / echo cancelling, and the cloud "ears":
 * Deepgram (Nova-3), Groq Whisper and Google Gemini. Audio is cut with a simple energy VAD (or by push-to-talk),
 * sent as a 16 kHz mono WAV, and the text comes back through the same callbacks the page already uses.
 * Also feeds the same cleaned audio into Android's SpeechRecognizer through a pipe (Android 13+).
 */
final class Hearing {

    static final int RATE = 16000;
    private static final int FRAME = 320; // 20 ms

    interface Cb {
        void onStart();
        void onLevel(float level);
        /** Recording finished, waiting for the transcript. */
        void onBusy();
        void onHeard(String text);
        void onNothing();
        /** The cloud engine failed (bad key, quota, network); the caller falls back to the phone's recognizer. */
        void onFail(String message);
    }

    static final class Engine {
        final String kind, key, model;
        Engine(String kind, String key, String model) { this.kind = kind; this.key = key; this.model = model; }
    }

    /** One recording; a new one never touches an older one's recorder or flags. */
    private static final class Session {
        volatile boolean stop, cancelled, done;
        AudioRecord rec;
        NoiseSuppressor ns;
        AcousticEchoCanceler aec;
        AutomaticGainControl agc;
    }

    private volatile Session session;

    boolean active() { Session s = session; return s != null && !s.done; }

    /** Ends a push-to-talk recording (the transcript follows), or ends a pipe feed. */
    void finish() { Session s = session; if (s != null) s.stop = true; }

    /** Drops whatever is recording; no callback follows. */
    void cancel() {
        Session s = session;
        if (s != null) { s.cancelled = true; s.stop = true; }
    }

    // ---------- recorder ----------

    private static boolean openRecorder(Session z) {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int size = Math.max(min, RATE * 2 / 2); // ~0.5 s buffer
        int[] sources = {MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
        for (int src : sources) {
            try {
                AudioRecord r = new AudioRecord(src, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
                if (r.getState() == AudioRecord.STATE_INITIALIZED) {
                    z.rec = r;
                    int sid = r.getAudioSessionId();
                    try { if (NoiseSuppressor.isAvailable()) { z.ns = NoiseSuppressor.create(sid); if (z.ns != null) z.ns.setEnabled(true); } } catch (Exception ignored) { }
                    try { if (AcousticEchoCanceler.isAvailable()) { z.aec = AcousticEchoCanceler.create(sid); if (z.aec != null) z.aec.setEnabled(true); } } catch (Exception ignored) { }
                    try { if (AutomaticGainControl.isAvailable()) { z.agc = AutomaticGainControl.create(sid); if (z.agc != null) z.agc.setEnabled(true); } } catch (Exception ignored) { }
                    return true;
                }
                r.release();
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static void closeRecorder(Session z) {
        try { if (z.rec != null) z.rec.stop(); } catch (Exception ignored) { }
        try { if (z.rec != null) z.rec.release(); } catch (Exception ignored) { }
        try { if (z.ns != null) z.ns.release(); } catch (Exception ignored) { }
        try { if (z.aec != null) z.aec.release(); } catch (Exception ignored) { }
        try { if (z.agc != null) z.agc.release(); } catch (Exception ignored) { }
        z.rec = null; z.ns = null; z.aec = null; z.agc = null;
    }

    /** Frame loudness on roughly the same scale as SpeechRecognizer's onRmsChanged (about -2 quiet … 10 loud). */
    static float level(short[] buf, int n) {
        double sum = 0;
        for (int i = 0; i < n; i++) sum += (double) buf[i] * buf[i];
        double rms = Math.sqrt(sum / Math.max(1, n));
        double db = 20 * Math.log10(Math.max(rms, 1) / 32768.0);
        return (float) Math.max(-2, Math.min(12, (db + 55) / 3.5));
    }

    // ---------- cloud listening ----------

    /**
     * Records one turn and transcribes it. ptt: record until finish() (max 30 s); otherwise an energy VAD ends the turn.
     * strict: TV mode (a louder voice is needed to start). silenceMs: the page's pause allowance for this turn.
     */
    void listenCloud(final Engine engine, final String lang, final long silenceMs, final boolean ptt, final boolean strict,
                     final List<String> keyterms, final Cb cb) {
        cancel();
        final Session z = new Session();
        session = z;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] wav;
                    try {
                        if (!openRecorder(z)) { if (!z.cancelled) cb.onFail("Mikrofon açılamadı"); return; }
                        z.rec.startRecording();
                        if (z.rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) { if (!z.cancelled) cb.onFail("Mikrofon başka bir uygulamada kullanılıyor olabilir"); return; }
                        if (z.cancelled) return;
                        cb.onStart();
                        wav = capture(z, silenceMs, ptt, strict, cb);
                    } catch (Exception e) {
                        if (!z.cancelled) cb.onFail("Mikrofon hatası");
                        return;
                    } finally {
                        closeRecorder(z);
                    }
                    if (z.cancelled) return;
                    if (wav == null) { cb.onNothing(); return; }
                    cb.onBusy();
                    try {
                        String text = clean(transcribe(engine, wav, lang, keyterms));
                        if (z.cancelled) return;
                        if (text.isEmpty()) cb.onNothing(); else cb.onHeard(text);
                    } catch (Exception e) {
                        if (!z.cancelled) cb.onFail(e.getMessage() == null ? "Ses tanıma servisine ulaşılamadı" : e.getMessage());
                    }
                } finally {
                    z.done = true;
                }
            }
        }, "hearing").start();
    }

    /** Returns a WAV of the speech, or null when nobody spoke. */
    private static byte[] capture(Session z, long silenceMs, boolean ptt, boolean strict, Cb cb) {
        short[] buf = new short[FRAME];
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        ArrayDeque<byte[]> preroll = new ArrayDeque<byte[]>();
        float ambSum = 0; int ambN = 0; float amb = 0;
        boolean speaking = ptt;
        int loudRun = 0, silentMs = 0, speechMs = 0, elapsed = 0, levelTick = 0;
        long endSilence = Math.max(700, Math.min(2500, (long) (silenceMs * 0.6)));
        int maxMs = ptt ? 30000 : silenceMs >= 3000 ? 30000 : 12000;
        int waitMs = silenceMs >= 3000 ? 10000 : 7000;
        float margin = strict ? 4f : 2.5f;
        while (!z.stop) {
            int n = z.rec.read(buf, 0, FRAME);
            if (n <= 0) break;
            elapsed += 20;
            float lv = level(buf, n);
            if (++levelTick % 3 == 0) cb.onLevel(lv);
            byte[] bytes = new byte[n * 2];
            for (int i = 0; i < n; i++) { bytes[2 * i] = (byte) buf[i]; bytes[2 * i + 1] = (byte) (buf[i] >> 8); }
            if (ptt) {
                pcm.write(bytes, 0, bytes.length);
                if (lv > 2) speechMs += 20;
                if (elapsed >= maxMs) break;
                continue;
            }
            if (elapsed <= 300) { ambSum += lv; ambN++; amb = Math.min(6f, ambSum / ambN); }
            if (!speaking) {
                preroll.addLast(bytes);
                if (preroll.size() > 20) preroll.removeFirst(); // 400 ms before the voice started
                if (elapsed > 300 && lv > amb + margin && lv > 1f) loudRun++; else loudRun = 0;
                if (loudRun >= 4) {
                    speaking = true;
                    for (byte[] b : preroll) pcm.write(b, 0, b.length);
                    preroll.clear();
                    speechMs = 80;
                } else if (elapsed > waitMs) {
                    return null;
                }
            } else {
                pcm.write(bytes, 0, bytes.length);
                if (lv < amb + margin * 0.6f) silentMs += 20; else { silentMs = 0; speechMs += 20; }
                if (silentMs >= endSilence || elapsed >= maxMs) break;
            }
        }
        if (z.cancelled || !speaking || speechMs < 200 || pcm.size() < RATE / 2) return null;
        return wav(pcm.toByteArray());
    }

    static byte[] wav(byte[] pcm) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(pcm.length + 44);
        writeStr(o, "RIFF"); writeInt(o, 36 + pcm.length); writeStr(o, "WAVE");
        writeStr(o, "fmt "); writeInt(o, 16); writeShort(o, 1); writeShort(o, 1); writeInt(o, RATE); writeInt(o, RATE * 2); writeShort(o, 2); writeShort(o, 16);
        writeStr(o, "data"); writeInt(o, pcm.length);
        o.write(pcm, 0, pcm.length);
        return o.toByteArray();
    }

    private static void writeStr(ByteArrayOutputStream o, String s) { for (int i = 0; i < s.length(); i++) o.write(s.charAt(i)); }
    private static void writeInt(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); o.write(v >> 16); o.write(v >> 24); }
    private static void writeShort(ByteArrayOutputStream o, int v) { o.write(v); o.write(v >> 8); }

    /** Drops the stock phrases speech models invent on silence or TV noise. */
    static String clean(String t) {
        if (t == null) return "";
        t = t.trim();
        String low = t.toLowerCase(new Locale("tr", "TR")).replaceAll("[\\[\\]().\"']", "").trim();
        if (low.isEmpty() || low.equals("boş") || low.equals("bos") || low.equals("empty") || low.equals("sessizlik")) return "";
        String[] junk = {"altyazı", "abone ol", "izlediğiniz için", "izlediğin için", "beğenmeyi unutma", "m.k.", "amara.org", "teşekkürler."};
        if (t.length() < 60) for (String j : junk) if (low.contains(j)) return "";
        return t;
    }

    // ---------- engines ----------

    static final String STT_PROMPT_WORDS = "Pamuk, Spotify, YouTube, şarkı, video, aç, kapat, masal, bilmece";

    static String transcribe(Engine e, byte[] wav, String lang, List<String> keyterms) throws Exception {
        String l = lang != null && lang.startsWith("en") ? "en" : "tr";
        if ("deepgram".equals(e.kind)) return deepgram(e, wav, l, keyterms);
        if ("groq".equals(e.kind)) return groq(e, wav, l, keyterms);
        return gemini(e, wav, l, keyterms);
    }

    static String deepgram(Engine e, byte[] wav, String lang, List<String> keyterms) throws Exception {
        String model = e.model == null || e.model.isEmpty() ? "nova-3" : e.model;
        try {
            return deepgramOnce(e.key, model, wav, lang, keyterms);
        } catch (HttpError err) {
            // An account or model without Turkish Nova-3 gets Nova-2 (which uses "keywords", not "keyterm").
            if (err.code == 400 && !"nova-2".equals(model)) return deepgramOnce(e.key, "nova-2", wav, lang, null);
            throw err;
        }
    }

    private static String deepgramOnce(String key, String model, byte[] wav, String lang, List<String> keyterms) throws Exception {
        StringBuilder url = new StringBuilder("https://api.deepgram.com/v1/listen?model=").append(model)
                .append("&language=").append(lang).append("&smart_format=true&punctuate=true");
        if (keyterms != null) for (String k : keyterms) url.append("&keyterm=").append(URLEncoder.encode(k, "UTF-8"));
        byte[] res = post(url.toString(), "audio/wav", wav, "Authorization", "Token " + key, "Deepgram");
        JSONObject j = new JSONObject(new String(res, "UTF-8"));
        JSONArray ch = j.getJSONObject("results").getJSONArray("channels");
        return ch.getJSONObject(0).getJSONArray("alternatives").getJSONObject(0).optString("transcript", "");
    }

    static String groq(Engine e, byte[] wav, String lang, List<String> keyterms) throws Exception {
        String model = e.model == null || e.model.isEmpty() ? "whisper-large-v3-turbo" : e.model;
        String boundary = "----pamuk" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String prompt = keyterms == null ? STT_PROMPT_WORDS : join(keyterms) + ", aç, kapat";
        String[][] fields = {{"model", model}, {"language", lang}, {"prompt", prompt}, {"response_format", "json"}, {"temperature", "0"}};
        for (String[] f : fields) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f[0] + "\"\r\n\r\n" + f[1] + "\r\n").getBytes("UTF-8"));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"ses.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes("UTF-8"));
        body.write(wav);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
        byte[] res = post("https://api.groq.com/openai/v1/audio/transcriptions", "multipart/form-data; boundary=" + boundary,
                body.toByteArray(), "Authorization", "Bearer " + e.key, "Groq");
        return new JSONObject(new String(res, "UTF-8")).optString("text", "");
    }

    static final String[] GEMINI_FALLBACK_MODELS = {"gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-flash-latest"};

    static String gemini(Engine e, byte[] wav, String lang, List<String> keyterms) throws Exception {
        String words = keyterms == null ? STT_PROMPT_WORDS : join(keyterms) + ", aç, kapat";
        String prompt = "en".equals(lang)
                ? "Transcribe this audio recording exactly as spoken (it is a child speaking English, maybe with a Turkish accent). Write only the spoken words. If there is no speech or only background TV sound, return an empty response."
                : "Bu ses kaydını Türkçe olarak aynen yazıya dök. Sadece söylenen sözleri yaz. Konuşma yoksa ya da sadece arka plan TV sesi varsa boş döndür. Olası kelimeler: " + words + ".";
        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray()
                        .put(new JSONObject().put("text", prompt))
                        .put(new JSONObject().put("inline_data", new JSONObject().put("mime_type", "audio/wav")
                                .put("data", java.util.Base64.getEncoder().encodeToString(wav)))))))
                .put("generationConfig", new JSONObject().put("temperature", 0));
        String first = e.model == null || e.model.isEmpty() ? GEMINI_FALLBACK_MODELS[0] : e.model;
        java.util.LinkedHashSet<String> models = new java.util.LinkedHashSet<String>();
        models.add(first);
        for (String m : GEMINI_FALLBACK_MODELS) models.add(m);
        HttpError last = null;
        for (String m : models) {
            try {
                byte[] res = post("https://generativelanguage.googleapis.com/v1beta/models/" + m + ":generateContent",
                        "application/json", body.toString().getBytes("UTF-8"), "x-goog-api-key", e.key, "Gemini");
                JSONObject j = new JSONObject(new String(res, "UTF-8"));
                JSONArray cands = j.optJSONArray("candidates");
                if (cands == null || cands.length() == 0) return "";
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                JSONArray parts = content == null ? null : content.optJSONArray("parts");
                StringBuilder out = new StringBuilder();
                for (int i = 0; parts != null && i < parts.length(); i++) {
                    JSONObject p = parts.getJSONObject(i);
                    if (p.optBoolean("thought", false)) continue;
                    out.append(p.optString("text", ""));
                }
                return out.toString();
            } catch (HttpError err) {
                last = err;
                if (err.code != 404) throw err; // only an unknown model name moves on to the next model
            }
        }
        throw last;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) { if (b.length() > 0) b.append(", "); b.append(s); }
        return b.toString();
    }

    static final class HttpError extends Exception {
        final int code;
        HttpError(int code, String m) { super(m); this.code = code; }
    }

    private static byte[] post(String url, String type, byte[] data, String hName, String hValue, String service) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(25000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", type);
        c.setRequestProperty(hName, hValue);
        c.setFixedLengthStreamingMode(data.length);
        OutputStream os = c.getOutputStream();
        os.write(data);
        os.close();
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
        if (code == 401 || code == 403) throw new HttpError(code, service + " anahtarı geçersiz ya da yetkisiz.");
        if (code == 429) throw new HttpError(code, service + " ücretsiz kotası şimdilik doldu.");
        if (code >= 400) {
            String msg = bo.toString("UTF-8");
            if (msg.length() > 160) msg = msg.substring(0, 160);
            throw new HttpError(code, service + " hatası (" + code + "): " + msg);
        }
        return bo.toByteArray();
    }

    // ---------- feeding Android's recognizer (Android 13+) ----------

    /**
     * Starts recording cleaned audio into a pipe; returns the read side for RecognizerIntent.EXTRA_AUDIO_SOURCE,
     * or null when the microphone could not be opened. Levels go to cb.onLevel; nothing else is called.
     */
    ParcelFileDescriptor startPipe(final Cb cb) {
        cancel();
        final Session z = new Session();
        session = z;
        final ParcelFileDescriptor[] pipe;
        try {
            if (!openRecorder(z)) { z.done = true; return null; }
            pipe = ParcelFileDescriptor.createPipe();
            z.rec.startRecording();
            if (z.rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                closeRecorder(z);
                z.done = true;
                try { pipe[0].close(); pipe[1].close(); } catch (Exception ignored) { }
                return null;
            }
        } catch (Exception e) {
            closeRecorder(z);
            z.done = true;
            return null;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]);
                short[] buf = new short[FRAME];
                byte[] bytes = new byte[FRAME * 2];
                int tick = 0;
                try {
                    while (!z.stop) {
                        int n = z.rec.read(buf, 0, FRAME);
                        if (n <= 0) break;
                        if (++tick % 3 == 0 && !z.cancelled) cb.onLevel(level(buf, n));
                        for (int i = 0; i < n; i++) { bytes[2 * i] = (byte) buf[i]; bytes[2 * i + 1] = (byte) (buf[i] >> 8); }
                        out.write(bytes, 0, n * 2); // throws once the recognizer closed its end
                    }
                } catch (Exception ignored) {
                } finally {
                    try { out.close(); } catch (Exception ignored) { }
                    closeRecorder(z);
                    z.done = true;
                }
            }
        }, "hearing-pipe").start();
        return pipe[0];
    }
}
