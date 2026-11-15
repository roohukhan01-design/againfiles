# Roohu ClipForge 2.0 (Android)

Turns long videos into vertical 9:16 clips, **autonomously**. Package `com.roohu.clipforge`.

## What changed in 2.0
- **Auto Create button**: one tap finds the best moments, writes the spoken words as captions, titles each clip, styles it by mood, adds music and saves everything to the Gallery.
- **Offline auto-captions** (Vosk) with real per-word timing. The spoken word pops and lights up. Styles: Pop, Karaoke, Bar, Outlined, or Auto (matches the clip mood).
- **Styled RoohuXKhan badge**: gradient pill, play icon, glowing X, shimmer sweep, slide-in. Position, size and opacity stay adjustable in Settings.
- **No more lag**: export now runs on the phone's hardware encoder (Media3 Transformer) at a constant 30 fps, H.264 + AAC MP4 with a proper index. Clips save to **Movies/RoohuClipForge**, so they show in the Gallery.
- **Built-in music**: if you add no tracks, a small original beat is generated per mood (calm, upbeat, intense). No copyright worries.
- Animations: zoom punch-in, beat pulse, fade in/out, vignette, animated progress bar.

## Build the APK on GitHub (no Android Studio)
1. Create an empty GitHub repository and upload **everything** in this folder, including the hidden `.github` folder.
2. Open **Actions**. The build starts on every push to `main` (or press **Run workflow**). The workflow downloads the two speech models (about 80 MB) by itself, so this takes a few minutes longer than before.
3. When it is green, download **Roohu-ClipForge-debug-apk** under Artifacts and install `app-debug.apk`.

If the build fails, open the failed step and send the first line that starts with `error:` or `What went wrong:`.

## Honest limits
- Offline captions support **English** and **Hindi/Urdu speech** (Urdu speech comes out in Devanagari script, because there is no small Urdu model). Other languages: import an .srt file.
- "Fit with blurred background" is exported as a clean letterbox. Fill mode (default) follows the motion.
- Music ducking under speech is not in the native export yet; music plays at the volume set in Settings.
- Claude title/rating needs claude.ai and is unavailable here; titles come from the first spoken words instead.
- YouTube links are not downloaded (needs a server).
- Debug-signed APK. A Play Store release needs your own signing key.

## 3.0 Milestone 2: reaction-edit look (new)
- Settings > **Edit look**: black frame with two glowing white dividers, a **3D hook title** in the top bar (red + yellow, extruded), and a **zoom punch + light flash + shake on every loud moment** found automatically from the clip's audio (`Energy.java`). Divider glow pulses with the hits.
- Applies to the phone export. The in-app preview stays plain.
- Next: M3 3D captions with bundled free fonts, M4 on-device scene scoring + mood music, M5 optional Gemini key.

## 3.1 Milestone 3: languages, real fonts, 3D look (new)
- **Any spoken language**: Settings > Spoken language is now *Auto-detect*. Paste a free Groq key (console.groq.com, no card) and Whisper detects the language itself (Urdu, Hindi, Arabic, English and 90+ more) with exact word timing. Without a key the app picks between its offline English and Hindi/Urdu models automatically. Right-to-left languages are laid out right-to-left.
- **Real fonts**: the GitHub build now downloads Anton, Bungee, Bangers, Luckiest Guy, Archivo Black, Poppins Black, Bebas Neue, Russo One, Black Ops One and Tajawal (Arabic) into `assets/fonts`. If a download fails the app falls back to a system font and the build still succeeds.
- **3D captions**: extruded letters with outline, gradient face, shine and a glow on the spoken word. Styles: 3D pop, Fire, Gold, Ice, Neon (auto by clip mood). Each caption page springs in.
- **Cinematic GPU effects** (`FxEffect.java`): chromatic aberration, glitch slices, bloom, grade, vignette and film grain, kicking on every loud moment.
- **Name credit**: the tiny logo pill is gone. "ROOHU X KHAN" is now big 3D chrome text (gold / red X / ice) with a moving shine, centred in the bottom bar.
