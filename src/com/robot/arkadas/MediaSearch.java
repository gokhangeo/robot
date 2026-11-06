package com.robot.arkadas;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds YouTube videos and Spotify music without API keys (Spotify keys are optional). Blocking; call off the main thread. */
final class MediaSearch {

    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36";

    private MediaSearch() { }

    // ---------- YouTube ----------

    /**
     * Searches YouTube's normal results page (videos only, Restricted Mode cookie on) and reads ytInitialData.
     * Returns [{id,title,sub,len,thumb}].
     */
    static JSONArray youtube(String query) throws Exception {
        String url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
                + "&sp=EgIQAQ%3D%3D&hl=tr&gl=TR"; // sp=EgIQAQ== → videos only
        // PREF f2=8000000 turns on YouTube's Restricted Mode for this request.
        String html = new String(get(url, "PREF=f2=8000000&hl=tr&gl=TR; CONSENT=YES+cb"), "UTF-8");
        return parseYoutube(html);
    }

    static JSONArray parseYoutube(String html) throws Exception {
        int i = html.indexOf("var ytInitialData = ");
        int start;
        if (i >= 0) start = i + "var ytInitialData = ".length();
        else {
            i = html.indexOf("ytInitialData\"] = ");
            if (i < 0) throw new Exception("YouTube sonuçları okunamadı");
            start = i + "ytInitialData\"] = ".length();
        }
        int end = html.indexOf(";</script>", start);
        if (end < 0) throw new Exception("YouTube sonuçları okunamadı");
        JSONObject data = new JSONObject(html.substring(start, end));
        JSONArray out = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<String>();
        collect(data, out, seen, 0);
        return out;
    }

    private static void collect(Object node, JSONArray out, LinkedHashSet<String> seen, int depth) throws Exception {
        if (out.length() >= 10 || depth > 40) return;
        if (node instanceof JSONObject) {
            JSONObject o = (JSONObject) node;
            JSONObject v = o.optJSONObject("videoRenderer");
            if (v != null) {
                String id = v.optString("videoId", "");
                if (id.length() == 11 && seen.add(id)) {
                    String len = v.optJSONObject("lengthText") != null ? v.getJSONObject("lengthText").optString("simpleText", "") : "";
                    if (!len.isEmpty()) { // no length = live stream; skip
                        out.put(new JSONObject()
                                .put("id", id)
                                .put("title", text(v.optJSONObject("title")))
                                .put("sub", text(v.optJSONObject("ownerText")))
                                .put("len", len)
                                .put("thumb", "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg"));
                    }
                }
                return;
            }
            JSONArray names = o.names();
            for (int k = 0; names != null && k < names.length(); k++) collect(o.opt(names.getString(k)), out, seen, depth + 1);
        } else if (node instanceof JSONArray) {
            JSONArray a = (JSONArray) node;
            for (int k = 0; k < a.length(); k++) collect(a.opt(k), out, seen, depth + 1);
        }
    }

    private static String text(JSONObject t) {
        if (t == null) return "";
        if (t.has("simpleText")) return t.optString("simpleText", "");
        JSONArray runs = t.optJSONArray("runs");
        StringBuilder b = new StringBuilder();
        for (int i = 0; runs != null && i < runs.length(); i++) b.append(runs.optJSONObject(i).optString("text", ""));
        return b.toString();
    }

    // ---------- Spotify ----------

    private static String spToken;
    private static long spTokenUntil;

    /**
     * Returns [{type,id,title,sub,thumb}] for Spotify. With a Client ID/Secret the official search is used; otherwise
     * open.spotify.com links are taken from a web search (DuckDuckGo, then Bing) and titled through Spotify's keyless oEmbed.
     */
    static JSONArray spotify(String query, String clientId, String clientSecret) throws Exception {
        if (clientId != null && !clientId.trim().isEmpty() && clientSecret != null && !clientSecret.trim().isEmpty()) {
            try { return spotifyApi(query, clientId.trim(), clientSecret.trim()); } catch (Exception ignored) { }
        }
        LinkedHashSet<String> links = new LinkedHashSet<String>();
        String q = URLEncoder.encode("site:open.spotify.com " + query, "UTF-8");
        String[] engines = {"https://html.duckduckgo.com/html/?q=" + q, "https://www.bing.com/search?q=" + q + "&setlang=tr"};
        for (String e : engines) {
            try {
                String html = new String(get(e, null), "UTF-8");
                findSpotifyLinks(html, links);
            } catch (Exception ignored) { }
            if (links.size() >= 3) break;
        }
        JSONArray out = new JSONArray();
        for (String l : links) {
            if (out.length() >= 6) break;
            String[] p = l.split("/");
            JSONObject item = new JSONObject().put("type", p[0]).put("id", p[1]).put("title", "").put("sub", "").put("thumb", "");
            try {
                JSONObject o = new JSONObject(new String(get("https://open.spotify.com/oembed?url="
                        + URLEncoder.encode("https://open.spotify.com/" + l, "UTF-8"), null), "UTF-8"));
                item.put("title", o.optString("title", "")).put("thumb", o.optString("thumbnail_url", ""));
            } catch (Exception ignored) { }
            out.put(item);
        }
        return out;
    }

    static void findSpotifyLinks(String html, LinkedHashSet<String> links) {
        String h = html;
        try { h = h + " " + URLDecoder.decode(html.replace("+", "%2B"), "UTF-8"); } catch (Exception ignored) { }
        Matcher m = Pattern.compile("open\\.spotify\\.com/(?:intl-[a-z]{2}/)?(track|playlist|album)/([A-Za-z0-9]{22})").matcher(h);
        while (m.find()) links.add(m.group(1) + "/" + m.group(2));
    }

    private static JSONArray spotifyApi(String query, String id, String secret) throws Exception {
        if (spToken == null || System.currentTimeMillis() > spTokenUntil) {
            HttpURLConnection c = (HttpURLConnection) new URL("https://accounts.spotify.com/api/token").openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            c.setRequestProperty("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString((id + ":" + secret).getBytes("UTF-8")));
            OutputStream os = c.getOutputStream();
            os.write("grant_type=client_credentials".getBytes("UTF-8"));
            os.close();
            if (c.getResponseCode() != 200) throw new Exception("Spotify anahtarı kabul edilmedi");
            JSONObject t = new JSONObject(new String(readAll(c.getInputStream()), "UTF-8"));
            spToken = t.getString("access_token");
            spTokenUntil = System.currentTimeMillis() + (t.optLong("expires_in", 3600) - 60) * 1000;
        }
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.spotify.com/v1/search?type=track,playlist&market=TR&limit=6&q="
                + URLEncoder.encode(query, "UTF-8")).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Authorization", "Bearer " + spToken);
        if (c.getResponseCode() != 200) throw new Exception("Spotify araması başarısız");
        JSONObject r = new JSONObject(new String(readAll(c.getInputStream()), "UTF-8"));
        JSONArray out = new JSONArray();
        JSONArray tracks = r.optJSONObject("tracks") != null ? r.getJSONObject("tracks").optJSONArray("items") : null;
        for (int i = 0; tracks != null && i < tracks.length(); i++) {
            JSONObject t = tracks.optJSONObject(i);
            if (t == null) continue;
            JSONArray artists = t.optJSONArray("artists");
            JSONArray images = t.optJSONObject("album") != null ? t.getJSONObject("album").optJSONArray("images") : null;
            out.put(new JSONObject().put("type", "track").put("id", t.optString("id"))
                    .put("title", t.optString("name"))
                    .put("sub", artists != null && artists.length() > 0 ? artists.getJSONObject(0).optString("name") : "")
                    .put("thumb", images != null && images.length() > 0 ? images.getJSONObject(images.length() - 1).optString("url") : ""));
        }
        JSONArray lists = r.optJSONObject("playlists") != null ? r.getJSONObject("playlists").optJSONArray("items") : null;
        for (int i = 0; lists != null && i < lists.length() && i < 3; i++) {
            JSONObject t = lists.optJSONObject(i);
            if (t == null) continue;
            JSONArray images = t.optJSONArray("images");
            out.put(new JSONObject().put("type", "playlist").put("id", t.optString("id"))
                    .put("title", t.optString("name")).put("sub", "Çalma listesi")
                    .put("thumb", images != null && images.length() > 0 ? images.getJSONObject(0).optString("url") : ""));
        }
        return out;
    }

    // ---------- http ----------

    static byte[] get(String url, String cookie) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.6");
        c.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.8");
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        int code = c.getResponseCode();
        if (code >= 400) { c.disconnect(); throw new Exception("HTTP " + code); }
        byte[] b = readAll(c.getInputStream());
        c.disconnect();
        return b;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }
}
