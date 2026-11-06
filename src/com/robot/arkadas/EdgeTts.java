package com.robot.arkadas;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Free Microsoft Edge "Read aloud" neural voices (the same service and protocol the open-source edge-tts project uses).
 * Plain Java with a minimal WebSocket client over SSLSocket, so it also runs on a desktop JVM for tests.
 */
final class EdgeTts {

    static final String TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    static final String CHROMIUM_FULL_VERSION = "143.0.3650.75";
    static final String CHROMIUM_MAJOR = "143";
    static final String SEC_MS_GEC_VERSION = "2-" + CHROMIUM_FULL_VERSION;
    static final String HOST = "speech.platform.bing.com";
    static final String PATH = "/consumer/speech/synthesize/readaloud/edge/v1";
    static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
            + CHROMIUM_MAJOR + ".0.0.0 Safari/537.36 Edg/" + CHROMIUM_MAJOR + ".0.0.0";

    /** Seconds the phone clock is off from Microsoft's (learned from a rejected handshake's Date header). */
    static volatile double clockSkewSeconds = 0;

    private static final SecureRandom RND = new SecureRandom();

    private EdgeTts() { }

    /** Full voice name the service expects, e.g. tr-TR-EmelNeural → Microsoft Server Speech Text to Speech Voice (tr-TR, EmelNeural). */
    static String longVoiceName(String voice) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([a-z]{2,})-([A-Z]{2,})-(.+Neural)$").matcher(voice);
        if (!m.matches()) return voice;
        String lang = m.group(1), region = m.group(2), name = m.group(3);
        if (name.contains("-")) { // e.g. zh-CN-liaoning-XiaobeiNeural
            region = region + "-" + name.substring(0, name.indexOf('-'));
            name = name.substring(name.indexOf('-') + 1);
        }
        return "Microsoft Server Speech Text to Speech Voice (" + lang + "-" + region + ", " + name + ")";
    }

    /**
     * Returns MP3 audio (24 kHz mono 48 kbps) for the text.
     * rate: "+0%" style, pitch: "+0Hz" style.
     */
    static byte[] synth(String text, String voice, String rate, String pitch) throws Exception {
        try {
            return synthOnce(text, voice, rate, pitch);
        } catch (SkewException e) {
            return synthOnce(text, voice, rate, pitch); // clock skew corrected, one retry
        }
    }

    private static final class SkewException extends Exception {
        SkewException(String m) { super(m); }
    }

    private static String connectId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String jsDate() {
        SimpleDateFormat f = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    static String escapeXml(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 8 || c == 11 || c == 12 || (c >= 14 && c <= 31)) { b.append(' '); continue; }
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&apos;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    private static byte[] synthOnce(String text, String voice, String rate, String pitch) throws Exception {
        String muid = hex(randomBytes(16));
        String query = "TrustedClientToken=" + TRUSTED_CLIENT_TOKEN + "&ConnectionId=" + connectId()
                + "&Sec-MS-GEC=" + SecMsGec.generate(System.currentTimeMillis() / 1000.0 + clockSkewSeconds)
                + "&Sec-MS-GEC-Version=" + SEC_MS_GEC_VERSION;

        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket(HOST, 443);
        try {
            SSLParameters p = s.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS"); // verify the certificate's host name
            s.setSSLParameters(p);
            s.setSoTimeout(15000);
            s.startHandshake();
            OutputStream out = s.getOutputStream();
            InputStream in = new java.io.BufferedInputStream(s.getInputStream());

            String key = android64(randomBytes(16));
            String req = "GET " + PATH + "?" + query + " HTTP/1.1\r\n"
                    + "Host: " + HOST + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold\r\n"
                    + "User-Agent: " + USER_AGENT + "\r\n"
                    + "Accept-Language: en-US,en;q=0.9\r\n"
                    + "Cookie: muid=" + muid + ";\r\n"
                    + "\r\n";
            out.write(req.getBytes("UTF-8"));
            out.flush();

            String head = readHttpHead(in);
            String status = head.substring(0, Math.max(0, head.indexOf("\r\n")));
            if (!status.contains(" 101")) {
                String date = header(head, "Date");
                if (date != null && status.contains(" 403")) {
                    try {
                        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                        double server = f.parse(date).getTime() / 1000.0;
                        double before = clockSkewSeconds;
                        clockSkewSeconds = server - System.currentTimeMillis() / 1000.0;
                        if (Math.abs(clockSkewSeconds - before) > 60) throw new SkewException("clock skew");
                    } catch (SkewException e) {
                        throw e;
                    } catch (Exception ignored) { }
                }
                throw new Exception("Edge ses sunucusu bağlantıyı reddetti (" + status + ")");
            }

            sendText(out, "X-Timestamp:" + jsDate() + "\r\n"
                    + "Content-Type:application/json; charset=utf-8\r\n"
                    + "Path:speech.config\r\n\r\n"
                    + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                    + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\""
                    + "},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n");
            String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                    + "<voice name='" + longVoiceName(voice) + "'>"
                    + "<prosody pitch='" + pitch + "' rate='" + rate + "' volume='+0%'>"
                    + escapeXml(text)
                    + "</prosody></voice></speak>";
            sendText(out, "X-RequestId:" + connectId() + "\r\n"
                    + "Content-Type:application/ssml+xml\r\n"
                    + "X-Timestamp:" + jsDate() + "Z\r\n"
                    + "Path:ssml\r\n\r\n" + ssml);

            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            ByteArrayOutputStream frag = new ByteArrayOutputStream();
            int fragOp = 0;
            while (true) {
                int b0 = in.read(), b1 = in.read();
                if (b0 < 0 || b1 < 0) throw new Exception("Edge ses bağlantısı kapandı");
                boolean fin = (b0 & 0x80) != 0;
                int op = b0 & 0x0f;
                long len = b1 & 0x7f;
                if (len == 126) len = (in.read() << 8) | in.read();
                else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | in.read(); }
                byte[] mask = null;
                if ((b1 & 0x80) != 0) { mask = new byte[4]; readFully(in, mask); }
                if (len > 8_000_000) throw new Exception("Edge: çok büyük çerçeve");
                byte[] payload = new byte[(int) len];
                readFully(in, payload);
                if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];

                if (op == 9) { sendFrame(out, 0xA, payload); continue; } // ping → pong
                if (op == 0xA) continue;
                if (op == 8) throw new Exception("Edge ses sunucusu bağlantıyı kapattı");
                if (op == 1 || op == 2) { fragOp = op; frag.reset(); }
                frag.write(payload);
                if (!fin) continue;
                byte[] msg = frag.toByteArray();
                frag.reset();
                if (fragOp == 1) {
                    String t = new String(msg, "UTF-8");
                    if (t.contains("Path:turn.end")) break;
                } else if (fragOp == 2 && msg.length >= 2) {
                    int hl = ((msg[0] & 0xff) << 8) | (msg[1] & 0xff);
                    if (2 + hl > msg.length) continue;
                    String h = new String(msg, 2, hl, "UTF-8");
                    if (h.contains("Path:audio") && h.contains("audio/mpeg")) audio.write(msg, 2 + hl, msg.length - 2 - hl);
                }
            }
            try { sendFrame(out, 8, new byte[0]); } catch (Exception ignored) { }
            if (audio.size() == 0) throw new Exception("Edge ses sunucusu ses göndermedi");
            return audio.toByteArray();
        } finally {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private static String readHttpHead(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int c = in.read();
            if (c < 0) throw new Exception("Edge ses sunucusu cevap vermedi");
            b.write(c);
            if ((state == 0 || state == 2) && c == '\r') state++;
            else if ((state == 1 || state == 3) && c == '\n') state++;
            else state = c == '\r' ? 1 : 0;
            if (b.size() > 65536) throw new Exception("Edge: geçersiz cevap");
        }
        return b.toString("ISO-8859-1");
    }

    private static String header(String head, String name) {
        for (String line : head.split("\r\n")) {
            int i = line.indexOf(':');
            if (i > 0 && line.substring(0, i).trim().equalsIgnoreCase(name)) return line.substring(i + 1).trim();
        }
        return null;
    }

    private static void readFully(InputStream in, byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new Exception("Edge ses bağlantısı yarıda kesildi");
            off += n;
        }
    }

    private static void sendText(OutputStream out, String text) throws Exception {
        sendFrame(out, 1, text.getBytes("UTF-8"));
    }

    /** Client frames must be masked (RFC 6455). */
    private static void sendFrame(OutputStream out, int opcode, byte[] payload) throws Exception {
        ByteArrayOutputStream f = new ByteArrayOutputStream(payload.length + 14);
        f.write(0x80 | opcode);
        int n = payload.length;
        if (n < 126) f.write(0x80 | n);
        else if (n < 65536) { f.write(0x80 | 126); f.write(n >>> 8); f.write(n & 0xff); }
        else { f.write(0x80 | 127); for (int i = 7; i >= 0; i--) f.write((int) (((long) n >>> (8 * i)) & 0xff)); }
        byte[] mask = randomBytes(4);
        f.write(mask);
        for (int i = 0; i < n; i++) f.write(payload[i] ^ mask[i & 3]);
        out.write(f.toByteArray());
        out.flush();
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RND.nextBytes(b);
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format("%02X", x & 0xff));
        return s.toString();
    }

    /** java.util.Base64 exists on Android 8+ (minSdk 26) and on desktop JVMs. */
    private static String android64(byte[] b) {
        return java.util.Base64.getEncoder().encodeToString(b);
    }
}
