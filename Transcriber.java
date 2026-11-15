package com.roohu.clipforge;

import android.content.Context;
import android.content.res.AssetManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Decodes a clip's audio to 16 kHz mono and runs offline recognition, returning caption pages with per-word timing. */
final class Transcriber {
    interface Prog { void on(int pct); }

    private static Model model;
    private static String modelKey;

    private static synchronized Model model(Context ctx, String key) throws IOException {
        if (model != null && key.equals(modelKey)) return model;
        if (model != null) { model.close(); model = null; }
        File dir = new File(ctx.getFilesDir(), "vosk/model-" + key);
        File ready = new File(dir, ".ready");
        if (!ready.exists()) {
            AssetManager am = ctx.getAssets();
            String src = "model-" + key;
            String[] top = am.list(src);
            if (top == null || top.length == 0)
                throw new IOException("The offline speech model (" + key + ") is not inside this APK. Build it with the GitHub workflow, which downloads the model.");
            deleteRec(dir);
            copyAssets(am, src, dir);
            if (!ready.createNewFile()) throw new IOException("Could not prepare the speech model.");
        }
        model = new Model(dir.getAbsolutePath());
        modelKey = key;
        return model;
    }

    private static void copyAssets(AssetManager am, String path, File dst) throws IOException {
        String[] kids = am.list(path);
        if (kids != null && kids.length > 0) {
            if (!dst.exists() && !dst.mkdirs()) throw new IOException("Cannot create " + dst);
            for (String k : kids) copyAssets(am, path + "/" + k, new File(dst, k));
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (InputStream in = am.open(path); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    private static void deleteRec(File f) {
        if (!f.exists()) return;
        File[] k = f.listFiles();
        if (k != null) for (File c : k) deleteRec(c);
        f.delete();
    }

    /**
     * Captions one clip. With a Groq key the spoken language is detected automatically by Whisper (any language).
     * Without a key, or if the cloud is unreachable, the offline models are used: English and Hindi/Urdu speech,
     * chosen automatically when the language is "auto".
     * Returns {cues:[{a,b,t,w:[[word,start,end]]}], lang, rtl, engine} in source-video seconds.
     */
    static JSONObject run(Context ctx, Uri uri, double startS, double endS, String lang, String key, Prog prog) throws Exception {
        boolean auto = lang == null || lang.isEmpty() || lang.startsWith("auto");
        String iso = auto ? null : lang.substring(0, 2).toLowerCase();
        Pcm16k pcm = Pcm16k.decode(ctx, uri, startS, endS, p -> prog.on(p * 30 / 100));
        if (pcm.data.length < 16000) throw new IOException("There is no speech in this part of the video.");

        List<Painter.W> words = null;
        String detected = "", engine = "offline", cloudErr = null;
        boolean rtl = false;
        if (key != null && key.trim().length() > 10) {
            try {
                prog.on(40);
                CloudAsr.Result r = CloudAsr.groq(pcm.data, key.trim(), iso, pcm.base);
                words = r.words; detected = r.lang; rtl = r.rtl; engine = "ai";
            } catch (Exception e) {
                cloudErr = e.getMessage();
            }
        }
        if (words == null) {
            String mk;
            if (auto) mk = probe(ctx, pcm.data);
            else mk = iso.equals("en") ? "en" : (iso.equals("hi") || iso.equals("ur")) ? "hi" : null;
            if (mk == null) {
                throw new IOException((cloudErr != null ? cloudErr + " " : "")
                        + "Offline captions cover English and Hindi/Urdu speech. Add the free Groq key in Settings to caption any language.");
            }
            words = vosk(ctx, pcm, mk, prog);
            detected = mk.equals("en") ? "english" : "hindi/urdu";
            rtl = false;
        }
        List<Painter.W> inside = new ArrayList<>();
        for (Painter.W w : words) if (w.a >= startS - 0.15 && w.a <= endS) inside.add(w);
        JSONObject out = new JSONObject();
        out.put("cues", group(inside));
        out.put("lang", detected);
        out.put("rtl", rtl);
        out.put("engine", engine);
        if (cloudErr != null) out.put("note", cloudErr);
        return out;
    }

    /** Picks the offline model (English or Hindi/Urdu) that understands the first seconds better. */
    private static String probe(Context ctx, byte[] pcm) throws Exception {
        int n = Math.min(pcm.length, 16000 * 2 * 12);
        byte[] head = java.util.Arrays.copyOf(pcm, n);
        double en = -1, hi = -1;
        try { en = score(model(ctx, "en"), head); } catch (Throwable ignored) { }
        try { hi = score(model(ctx, "hi"), head); } catch (Throwable ignored) { }
        if (en < 0 && hi < 0) throw new IOException("The offline speech models are not inside this APK. Build it with the GitHub workflow.");
        return hi > en ? "hi" : "en";
    }

    private static double score(Model m, byte[] pcm) throws Exception {
        Recognizer r = new Recognizer(m, 16000f);
        try {
            r.setWords(true);
            double sum = 0;
            for (int off = 0; off < pcm.length; off += 8000) {
                int len = Math.min(8000, pcm.length - off);
                if (r.acceptWaveForm(java.util.Arrays.copyOfRange(pcm, off, off + len), len)) sum += conf(r.getResult());
            }
            return sum + conf(r.getFinalResult());
        } finally { r.close(); }
    }

    private static double conf(String json) throws Exception {
        JSONArray a = new JSONObject(json).optJSONArray("result");
        double s = 0;
        if (a != null) for (int i = 0; i < a.length(); i++) s += a.getJSONObject(i).optDouble("conf", 0.5);
        return s;
    }

    private static List<Painter.W> vosk(Context ctx, Pcm16k pcm, String key, Prog prog) throws Exception {
        Model m = model(ctx, key);
        List<Painter.W> words = new ArrayList<>();
        Recognizer rec = new Recognizer(m, 16000f);
        try {
            rec.setWords(true);
            int total = pcm.data.length, lastPct = -1;
            for (int off = 0; off < total; off += 8000) {
                int len = Math.min(8000, total - off);
                if (rec.acceptWaveForm(java.util.Arrays.copyOfRange(pcm.data, off, off + len), len))
                    collect(rec.getResult(), pcm.base, words);
                int pct = 30 + (int) (off * 69L / Math.max(1, total));
                if (pct != lastPct) { lastPct = pct; prog.on(pct); }
            }
            collect(rec.getFinalResult(), pcm.base, words);
        } finally { try { rec.close(); } catch (Exception ignored) { } }
        return words;
    }

    private static void collect(String json, double base, List<Painter.W> out) throws Exception {
        JSONObject o = new JSONObject(json);
        JSONArray r = o.optJSONArray("result");
        if (r == null) return;
        for (int i = 0; i < r.length(); i++) {
            JSONObject w = r.getJSONObject(i);
            out.add(new Painter.W(w.getString("word"), base + w.getDouble("start"), base + w.getDouble("end")));
        }
    }

    /** Pages of up to 4 words; a new page also starts after a pause or when a page runs long. */
    private static JSONArray group(List<Painter.W> ws) throws Exception {
        JSONArray out = new JSONArray();
        List<Painter.W> cur = new ArrayList<>();
        for (int i = 0; i <= ws.size(); i++) {
            boolean flush = i == ws.size();
            if (!flush && !cur.isEmpty()) {
                Painter.W prev = cur.get(cur.size() - 1), w = ws.get(i);
                if (cur.size() >= 4 || w.a - prev.b > 0.7 || w.b - cur.get(0).a > 2.4) flush = true;
            }
            if (flush && !cur.isEmpty()) {
                JSONObject c = new JSONObject();
                JSONArray wa = new JSONArray();
                StringBuilder t = new StringBuilder();
                for (Painter.W w : cur) {
                    JSONArray one = new JSONArray();
                    one.put(w.w).put(w.a).put(w.b);
                    wa.put(one);
                    if (t.length() > 0) t.append(' ');
                    t.append(w.w);
                }
                c.put("a", cur.get(0).a);
                c.put("b", cur.get(cur.size() - 1).b);
                c.put("t", t.toString());
                c.put("w", wa);
                out.put(c);
                cur = new ArrayList<>();
            }
            if (i < ws.size()) cur.add(ws.get(i));
        }
        return out;
    }
}
