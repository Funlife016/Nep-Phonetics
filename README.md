# Nepali IME (physical keyboard)

A minimal Android IME that transliterates QWERTY key sequences typed on a
**physical/hardware keyboard** into Devanagari (Nepali), e.g. `t` → त, and
`t` immediately followed by `h` → थ. Built with no soft-keyboard UI on
purpose — it stays invisible and lets the hardware keyboard do the work.

## Repo layout

```
app/src/main/assets/nepali_map.json        <- the ENTIRE transliteration mapping
app/src/main/java/.../MappingTable.kt      <- loads + indexes the JSON above
app/src/main/java/.../TransliterationEngine.kt  <- buffering/timeout logic (no Android deps)
app/src/main/java/.../NepaliImeService.kt  <- InputMethodService, wires physical key events to the engine
app/src/main/res/xml/method.xml            <- IME metadata required by Android
.github/workflows/build.yml                <- builds a debug APK on every push
```

## Tinkering with the mapping (no code changes needed)

Everything about *what maps to what* lives in `nepali_map.json`. Edit
triggers, add aliases, add new conjuncts, etc. — the engine reads this file
at runtime, so most tuning is just editing JSON and rebuilding. See the
`meta.notes` field inside the JSON itself for the schema explanation.

To swap in a totally different script/language later, drop in a new JSON
file with the same shape and change the `assetFileName` default in
`MappingTable.kt`.

## Building via GitHub Actions (no local Android SDK needed)

1. Push this repo to GitHub (create a new repo, then from a shell:
   `git init && git add . && git commit -m "init" && git remote add origin <your-repo-url> && git push -u origin main`).
   From the tablet, GitHub's mobile web UI also supports drag-and-drop
   file uploads if you'd rather not use git directly.
2. Go to the **Actions** tab of the repo on GitHub — the `Build APK`
   workflow runs automatically on every push to `main`.
3. Once it finishes (green check), open the workflow run and download the
   `nepali-ime-debug-apk` artifact — it's a zip containing `app-debug.apk`.
4. On the tablet: unzip if needed, tap the `.apk` to install (you may need
   to allow "install unknown apps" for your browser/files app first).

## Enabling the IME on the tablet

1. **Settings → System → Languages & input → On-screen keyboard** (or
   **Manage keyboards** — wording varies by OEM) and enable **Nepali IME**.
2. Tap into any text field, then switch input methods — usually via the
   keyboard icon in the notification bar/status bar, or long-press the
   spacebar on your physical keyboard on stock Android.
3. Type normally on the physical keyboard; Devanagari should appear.

## Known limitations (tracked as TODOs in code)

- **General consonant clusters** (e.g. स्त, न्द) via virama-stacking aren't
  implemented yet — only the explicit conjuncts listed in the JSON
  (क्ष, ज्ञ, त्र, श्र) render correctly today. Typing two ordinary
  consonants in a row currently just commits the first with its inherent
  vowel. This is the next real feature to build.
- No handling yet for `inputType` (e.g. it'll try to transliterate inside
  password fields — should be disabled there).
- No subtype/locale registration in `method.xml`, so it won't show up
  grouped under "Nepali" in system language pickers yet — cosmetic, easy
  to add later.

## Testing the engine logic without a device

`TransliterationEngine` has zero Android framework dependencies (just
`android.os.Handler`/`Looper` for the timeout, which can be swapped for a
plain `java.util.Timer` if you want fully offline JVM unit tests without
Robolectric). Worth adding a `src/test` module once the mapping stabilizes.
