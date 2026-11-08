# Changelog

## 2.1 — compact layout fixes

* **Fixed the bottom row being clipped.** The IME view now honours window insets:
  gesture-navigation devices pad the bottom by the navigation-bar inset, so the
  `↵` key is no longer cut off by the gesture handle. The measured height is also
  clamped to the height the window actually offers (and to 45 % of the screen),
  instead of always claiming the configured height and getting clipped.
* **Actually compact.** Default height 232 → **196 dp**, range 340 → **300 dp**
  (minimum 150), candidate strip 40 → **32 dp**, key gaps 2.6 → **2 dp**, key
  corner radius 6.5 → 5.5 dp, letter glyphs 19 → 17.5 dp, function glyphs
  13.5 → 12 dp. The keyboard now occupies ~22 % of a 891 dp screen instead of ~31 %.
* Clipboard panel header trimmed to 36 dp to match.
* Settings slider range updated to the new 150–300 dp bounds.
* `versionCode 3`, `versionName 2.1`; screenshots re-rendered at the new default.

## 2.0 — full rebuild of the starter project

The starter contained three files: a 28-entry hardcoded `mapOf` dictionary, a
28-character "traditional" map, and a 223-line service that rendered ~40 `TextView`
keys, stored clipboard items in an unordered `StringSet`, and had two keys that
did nothing (`123`, `⇧`). Everything below replaces that.

### Pinyin engine

* **Removed** the hardcoded 28-word map. Added a compiled offline dictionary of
  **189 731 entries** (67 717 words + 29 731 characters after de-duplication) built
  from Rime luna-pinyin, Rime essay, mozillazg/phrase-pinyin-data and OpenCC.
* Added `AssetDictionary`: a single 5.3 MB byte array with sorted ASCII pinyin
  keys, binary search over raw bytes, no per-entry `String` allocation.
* Added `PinyinSegmenter`: syllable segmentation with a 421-syllable table,
  alternative parses (`xian` → 先 / 西安), apostrophe-free tolerant input and a
  partial trailing syllable (`nih` → 你好).
* Added `PinyinEngine` ranking: exact whole-word matches, longer-phrase prefix
  matches, dictionary weight from a real corpus, user frequency, learned bigrams.
* Added word-level sentence composition (beam search over syllable spans) so
  `jintiantianqihenhao` produces 今天天气很好 instead of six random homophones.
* Segmentations are now ordered by dictionary evidence, which fixed
  `keneng` → 可能 (previously 垦嗯).
* Fixed case handling: `NiHao`, `ZHONGGUO` and tone-marked input all work.
* Composition now uses `setComposingText`, so the pinyin is underlined in the
  target field and replaced atomically; backspace edits the composition before it
  touches the field.

### Simplified / Traditional

* **Removed** the 28-character map and its broken reverse lookup.
* Added `TraditionalConverter` built from OpenCC `STCharacters` + `STPhrases`
  (53 289 entries, longest-match), post-processed with `TWVariants` so output uses
  Taiwan-standard glyphs (為, 吃, 裡, 啟).
* The 中/EN mode now decides what is committed: 中国 / 中國, 谢谢 / 謝謝,
  学习 / 學習, 干净 / 乾淨, 干部 / 幹部.

### Keyboard UI

* Replaced ~40 child views with one canvas-drawn `KeyboardView` (flat hierarchy,
  no relayout while typing, no jitter).
* Compact geometry: 10-unit grid, 2.6 dp gaps, 6.5 dp corner radius, default
  height 232 dp (170–340 dp adjustable), key text scales 85–125 %.
* Added press-scale animation, key preview popup, repeat-delete on backspace
  long-press, mode switch on space long-press, settings on ☰ long-press.
* **Implemented the previously dead keys**: `123` opens a real symbol page,
  `⇧` now cycles shift → caps lock, ↵ respects `imeOptions` editor actions,
  comma/period become ，。 in Chinese mode.
* Removed the toolbar row; all chrome now lives on the keys themselves.

### Clipboard

* **Replaced** `SharedPreferences.getStringSet` (unordered, lossy) with a Room
  database: `clipboard_entries(id, text, createdAt)`.
* Newest first, de-duplicated, capped at 100 (older rows are trimmed).
* Panel inside the keyboard: tap to paste, copy button, per-entry delete, clear
  all, and an explanatory note when the platform blocks clipboard reads.
* Separate `ClipboardActivity` for full management with timestamps.
* Capture is explicit and documented: on keyboard open and on panel open, never
  in the background.

### Settings and themes

* Added a real settings screen: keyboard height, key size, key sound, vibration
  and strength, pinyin suggestions, candidate bar, default Traditional, clipboard
  capture, clipboard manager, reset learned words, live status of the IME.
* **Replaced** the fake "⚙ toggles dark mode" button with a proper theme setting
  (System / White / Dark) that re-themes the keyboard instantly through a
  `SharedPreferences` listener.
* White theme: white surface, light grey keys, dark text. Dark theme: dark
  surface, grey keys, white text.

### Architecture and correctness

* Split the single-file service into `core`, `data`, `engine`, `input`, `ui`.
* Added `UserModel` (Room-backed learned words + bigrams) used to re-rank
  candidates and to predict the next word.
* Fixed IME lifecycle: `onStartInputView` resets composition, `onFinishInputView`
  flushes learning and finishes composition, `onUpdateSelection` drops a stale
  composition when the caret moves.
* Added `onEvaluateFullscreenMode() = false` so landscape does not go fullscreen.
* Added `deleteSurroundingTextInCodePoints` so backspace never splits an emoji.
* Manifest: only `VIBRATE`; backup rules exclude the clipboard database.
* Added adaptive launcher icon, Material 3 themes and three activities
  (settings, clipboard, test field).

### Tests

* 33 JVM tests (`./gradlew testDebugUnitTest`), all green: engine acceptance
  tests, ranking dump, performance guard rails, Robolectric UI hit-testing and
  activity/service inflation.
* Measured: 0.9 ms uncached candidate lookup, 0.02 ms cached, ~70 ms dictionary
  load on the JVM.

### Build

* `versionCode 2`, `versionName "2.0"`, `minSdk 29`, `targetSdk 35`, Gradle 8.11.1,
  AGP 8.7.3, Kotlin 2.0.21, KSP, Room 2.6.1.
* Debug and signed release APKs produced; signing config, ProGuard rules and
  wrapper included.
