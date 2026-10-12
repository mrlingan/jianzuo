# Jianzuo · 简作

[中文](README.md) · **English**

A quiet, native writing space. The SwiftUI / Xcode project supports **Mac, iPhone, and iPad**; the native Android Studio project supports **Android phones and tablets**. Neither app requires third-party libraries at runtime.

[Download](https://github.com/mrlingan/jianzuo/releases) · [Release notes (Chinese)](Docs/Releases/v1.0.0.md) · [Report an issue](https://github.com/mrlingan/jianzuo/issues) · [Contribute](CONTRIBUTING.md)

## Android phones and tablets

A native Android Studio project is available in [`Android`](Android). Open that directory in Android Studio and run the `app` configuration. Android 8.0+ is supported. A single APK adapts to phone navigation, tablet list/editor panes, wide three-pane windows, and split screen.

The Android app includes native writing, notebooks, search, favorites, Markdown preview, focus mode, atomic autosave, trash and restore, UTF-8 Markdown/TXT import and export, writing metrics, goals, fonts, and light/dark appearance. Each platform stores its own local library; Markdown/TXT files exchange document content.

```sh
bash Scripts/build_android.sh
```

The installable debug APK is written to `Build/Android/jianzuo-android-debug.apk`. See the [Android project guide](Android/README.md) and [validation record](Docs/Android-validation.md) (Chinese).

## Screenshots

### Mac: notebooks, document list, and editor

Browse notebooks and favorites on the left, search and switch documents in the middle, and write on the right. The footer shows save status, word count, and the current document's writing goal.

![Mac three-column interface with notebooks, a document list, Markdown source, and a writing goal](Docs/Screenshots/mac-writing.jpg)

<details>
<summary>See Markdown preview and focus mode</summary>

### Markdown preview

Use the eye icon or `⇧⌘P` to switch between Markdown source and a formatted preview of headings, paragraphs, lists, and quotations.

![Mac Markdown preview showing headings, paragraphs, and a quotation](Docs/Screenshots/mac-preview.jpg)

### Focus mode

Use the expand icon or `⇧⌘F` to hide navigation columns and concentrate on the current document. Toggle it again to return; on Mac, `Esc` also exits focus mode.

![Mac focus mode with navigation columns hidden and the document centered](Docs/Screenshots/mac-focus.jpg)

</details>

### iPhone / iPad: a workspace that adapts to the screen

iPhone uses a document list and a separate editing screen. Wider iPad windows show the list and editor side by side, with a sidebar that can be expanded when needed.

<table>
  <tr><th>iPhone · Writing</th><th>iPad · Browsing and writing</th></tr>
  <tr>
    <td align="center"><img src="Docs/Screenshots/iphone-writing.png" width="240" alt="iPhone editor with preview, favorite, export, and writing-goal controls"></td>
    <td align="center"><img src="Docs/Screenshots/ipad-writing.png" width="620" alt="Landscape iPad interface with a document list beside the editor"></td>
  </tr>
</table>

These are actual v1.0.0 screenshots using the included sample documents. Mobile screenshots were captured in iPhone / iPad simulators. **The Apple app interface is currently in Chinese; the README is available in Chinese and English. Android 1.11.0 adds Chinese and English interface languages.**

## Features and how to use them

| Feature | Usage |
| --- | --- |
| Native editing | Create a document and edit its title and body. NSTextView on Mac and UITextView on iOS support Chinese input, text selection, and native undo. |
| Notebooks and search | Use the built-in Drafts (文稿), Ideas (灵感), and Journal (日记), or create and rename custom notebooks. Search matches titles, body text, and tags; documents are sorted by their latest edit. |
| Markdown preview | Toggle the eye icon to preview headings, bold, italics, links, quotations, ordered / unordered lists, fenced code blocks, and horizontal rules. |
| Favorites and organization | Use the star icon to favorite a document. The More menu can duplicate documents, move them to another notebook, or send them to Trash. Mac document cards also have a context menu. |
| Trash and recovery | Trashed documents can be restored. Permanent deletion requires confirmation and cannot be undone. |
| Import and export | Import UTF-8 Markdown / TXT from the document list's More menu, with a preview for selection, renaming, and ordering. Export the body using the share icon: Markdown or TXT on Mac, Markdown on iOS. |
| Local autosave | Edits save about 450 ms after typing pauses, and immediately when the app leaves the foreground. Writes use atomic replacement; a library read failure preserves the original file and displays an error. |
| Counts and goals | Chinese characters count individually; English text counts by word. More → Document Statistics shows characters and estimated reading time. Choose a per-document goal in settings, or disable it. |
| Writing preferences | Choose Songti, the system font, or a monospaced font; adjust font size from 14 to 26 and line spacing; use system, light, or dark appearance. |
| Focus mode | Hide navigation columns while keeping the document and save status visible. Focus mode can be combined with Markdown preview. |

### Apple 1.1.0: more local writing tools

Mac, iPhone, and iPad now add custom notebooks, tags, read-only documents, up to 40 revisions, SHA-256 verified library backups, and the latest 10 local automatic backups. This update also includes selective find and replace, outline navigation, formatting previews, two-document editing, scratch notes, daily word counts, configurable typing shortcuts, and five palettes with ruled paper.

Use the sidebar for Backup & Recovery, Daily Writing, and Scratch Notes; open the editor's More menu for document tools. Wide windows show two editors side by side, while phones and smaller windows use a switcher. See the [Apple feature and validation record](Docs/Apple-feature-parity.md) for details and remaining Android gaps.

Apple v1 libraries migrate to v2 on save; Apple 1.0 cannot read the upgraded format. Full Apple backups can be exchanged between Apple devices. Android backups use a different format; exchange Markdown / TXT across platforms.

### Start writing

1. Select “开始新的一页” (Start a new page), then enter a title and body. Changes save automatically.
2. Organize documents into notebooks or star frequently used documents.
3. Use the eye icon to check formatting, or the expand icon to enter focus mode.
4. Export a Markdown copy when finished. You can import that file on another device.

### Mac keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| `⌘N` | New document |
| `⌘O` | Import documents |
| `⌘S` | Save immediately |
| `⇧⌘E` | Export the current document |
| `⇧⌘P` | Toggle Markdown editing / preview |
| `⇧⌘F` | Toggle focus mode |
| `Esc` | Exit focus mode |

## Download and install

Packages and checksum files are available on [GitHub Releases](https://github.com/mrlingan/jianzuo/releases).

| Package | Purpose and requirements |
| --- | --- |
| Mac DMG / ZIP | macOS 14 or later; includes Apple Silicon (arm64) and Intel (x86_64). Move “简作.app” into Applications. |
| Unsigned iOS IPA | iOS / iPadOS 17 or later, arm64. Built directly from source, unencrypted and unsigned. **Re-sign with your own valid certificate and provisioning profile before installing on a device.** |
| iOS Simulator ZIP | For Xcode simulators only; cannot be installed on physical devices. The current package was built with the iOS 27 SDK. |

Mac packages use an ad-hoc signature and do not have Developer ID signing or Apple notarization. macOS may show a security prompt on first launch; verify the download source and follow the system instructions. The unsigned IPA has not been installed and tested on a physical iPhone or iPad.

Start a simulator, then run these commands from the directory containing the extracted simulator app:

```sh
xcrun simctl install booted Jianzuo.app
xcrun simctl launch booted com.jianzuo.writing
```

## Data and current scope

- Documents are stored locally in `Application Support/Jianzuo/library.json`. The sandboxed Mac app stores this inside its own container.
- Each device has an independent library. **iCloud sync is not implemented.** Use import / export to exchange files and regularly export important documents as backups.
- Exports contain the body, with the title used as the filename. Notebook assignment, favorite status, creation time, and other library metadata are not included.
- Markdown preview does not render images, tables, or HTML. Their source text is still preserved and exported.
- Writing goals apply to the current document, rather than a daily total.
- Documentation is bilingual; app-interface localization into other languages is not implemented.

## Run in Xcode

Minimum OS versions: macOS 14 and iOS / iPadOS 17. No third-party libraries are required.

1. Clone the repository and open `Jianzuo.xcodeproj`.
2. Select the `Jianzuo` scheme and `My Mac`, an iPhone simulator, or an iPad simulator, then press `⌘R`.
3. To run on a physical device, select your development team in Signing & Capabilities, connect the device, and run.

```sh
git clone https://github.com/mrlingan/jianzuo.git
cd jianzuo
open Jianzuo.xcodeproj
```

### Build and test

```sh
# Mac unit tests
xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo \
  -destination 'platform=macOS' -derivedDataPath Build/Mac \
  CODE_SIGNING_ALLOWED=NO test

# iOS simulator build
xcodebuild -project Jianzuo.xcodeproj -scheme Jianzuo \
  -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath Build/iOS CODE_SIGNING_ALLOWED=NO build
```

All 15 Mac unit tests pass, covering counts, storage and corrupt-file protection, v1 migration, verified backups, history, notebooks and tags, selective Unicode replacement, outlines, and import previews. The iOS Simulator build succeeds; no usable simulator runtime is installed on this machine, so mobile runtime checks remain pending. See the [Apple validation record](Docs/Apple-feature-parity.md).

### Package a release

```sh
# Mac DMG / ZIP, simulator ZIP, unsigned device IPA, and checksums
bash Scripts/package_release.sh 1.0.0

# Unsigned device IPA only
bash Scripts/package_ipa.sh 1.0.0
```

Output: `Build/Releases/v1.0.0`. `SHA256SUMS.txt` covers Mac and simulator packages; the IPA has a separate `.ipa.sha256` file.

### Project layout

| Path | Contents |
| --- | --- |
| `Jianzuo/Models` | Documents, counts, local persistence, and library operations |
| `Jianzuo/Views` | Cross-platform UI, native editors, Markdown preview, and settings |
| `JianzuoTests` | Persistence and document-behavior tests |
| `Scripts` | Project generation, icon drawing, and release packaging |
| `Docs/Screenshots` | Actual UI screenshots used by the READMEs |
| `Docs/Releases` | Release notes |

`Scripts/create_project.py` regenerates the Xcode project and color assets. The editable Sketch icon source is `Design/AppIcon/Jianzuo-Icon-Design.sketch`; `Scripts/make_icon.swift` generates app icon assets from its exported masters.

## Contributing and license

Report bugs and suggest improvements through [Issues](https://github.com/mrlingan/jianzuo/issues), or submit a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines in Chinese.

Licensed under the [MIT License](LICENSE).

### Android 1.1.0 writing extensions

The Android app now adds document history, verified full-library backups, custom notebooks, chapter groups and tags, find and replace, formatting previews, outlines, a second editor, scratch notes, daily statistics, typing shortcuts, and PDF / PNG / HTML / ZIP export. See the [APK feature comparison](Docs/PureWriter-analysis.md) for implemented behavior and remaining differences. Cloud sync remains unavailable; Android 1.12.0 adds DeepSeek AI continuation.

### Android 1.2.0 local features

Adds revision diffs, selective replacement, multi-file import with editable titles and order, paragraph movement, daily goals and yearly summaries, notebook home-screen shortcuts, and DOCX / EPUB export with rich clipboard copy. HTML now renders the supported Markdown subset. Cloud sync remains unavailable; DeepSeek continuation is available in 1.12.0; see the [feature comparison](Docs/PureWriter-analysis.md).

### Android 1.3.0 organization

Independent volumes (including empty ones), collapsible chapter directories, exact tag filters, chapter dragging, and previewed batch numbering are available. Whole volumes can be moved, trashed, restored, or dissolved while keeping their text. Book exports follow volume and chapter order. Library schema 3 migrates earlier libraries; older app versions cannot read the upgraded format.

### Android 1.4.0 dual drafts

Two live native editors now support independent previews, document selection, saved positions, adjustable space allocation, and optional relative linked scrolling. Wide windows place drafts side by side; narrow windows stack them. Both use autosave, history, and daily word metrics. Chapters can be inserted before or after an existing chapter, and dragging near the directory edges scrolls to hidden destinations.

### Android 1.5.0 editor preferences

The shortcut toolbar supports one to three independently scrolling rows, visibility controls, drag or menu ordering, and multiline custom text. Changes are saved after review; resetting the layout preserves custom entries. Tap the status count to choose mixed words, Unicode characters with or without whitespace, or Han characters. Editable body selections show selection and document totals, including the active draft in the dual editor. Writing goals, directory totals and daily statistics continue to use mixed words. These preferences stay on the device; library format 3 is unchanged.

### Android 1.6.0 writing time

Title and body edits start a shared writing timer for both panes. It stops after 60 seconds without edits or when the app pauses, loses window focus, or rebuilds the editor. Daily and annual reviews include elapsed writing time, with monthly totals. Time recording can be disabled while keeping existing records. Backup recovery optionally restores statistics using the maximum for each date, avoiding repeated additions. Library schema 4 reads schemas 1–3; upgraded libraries and backups require Android 1.6.0 or later.

### Android 1.7.0 folder and system imports

The system folder picker scans UTF-8 Markdown/TXT recursively and groups the first directory level into notebooks. Text and files shared or opened from other apps use the same editable import preview. Rotation preserves reviewed titles, ordering, and selections. Large exports use private temporary snapshots instead of placing manuscript bodies in saved UI state. See the [Android audit](Docs/Android-unfinished-audit.md) for remaining features and limits.

### Android 1.8.0 scan controls and content recovery

Folder scans now show progress, support cancellation across rotation, and sort numeric filename runs naturally. Backup & Recovery can search titles and bodies across live documents, trash, revision history, and the latest 24 local snapshots. Preview a matching version and save it as a new editable document while preserving its source. See the [Android guide](Android/README.md) for search limits and lifecycle behavior.

### Android 1.9.0 formatted PDF and PNG exports

PDF and long-image exports now offer Markdown formatting or literal source layout. Native typesetting supports headings, inline styles, links with URLs, lists, tasks, quotes, code, dividers, and basic tables, including repeated table headers across PDF pages. Rendering finishes in private storage before opening the destination, so layout failures and size limits preserve existing files. Images, math, raw HTML, and tables wider than eight columns remain text; external resources are not loaded.

### Android 1.10.0 themes and ruled paper

Writing Settings now includes five built-in color palettes with light and dark variants, plus plain or ruled paper and a live preview before saving. Rules follow native text lines in single and dual editors and plain read-only views. Preferences stay on this device; documents, backups, and export styles remain unchanged. Custom colors, background images, and theme file import are not included.


### Android 1.11.0 interface languages

Writing settings → Interface language offers Follow system, 简体中文, and English. Tap Done to apply; Cancel keeps the current choice. Android 13+ shares the choice with the system per-app language setting; Android 8–12 stores a local override. Chinese system languages use Simplified Chinese; other unsupported languages fall back to English.

Menus, settings, statistics, dual editors, import previews, recovery, and built-in shortcut labels use paired resources. Switching languages preserves document text, notebook names, history, and custom shortcut labels. Included sample documents remain in their original language. Additional languages and the Apple interface translation remain unfinished. See the [Android validation record](Docs/Android-validation.md).


### Android 1.12.0 DeepSeek AI continuation

Enter your own API Key in **Writing settings → DeepSeek AI settings**, test the connection, and save. Open **Document menu → Writing tools → AI continuation** to generate an editable preview and insert it at the cursor. Includes an optional direction, three length choices, cancellation, regeneration, copying, and an original-text history checkpoint. Changed or unavailable documents cannot be overwritten by stale results. Both interface languages are supported.

The Key is encrypted locally with Android Keystore and excluded from manuscript backups and the APK. Requests go directly to DeepSeek and are charged to the user's account. Chinese and English requests were verified using an ephemeral user-supplied Key, including requests from Android. See the [Android guide](Android/README.md) for context limits and the [validation record](Docs/Android-validation.md) for actual results. Proofreading, chat, real-time suggestions, Apple AI, and cloud sync remain outside this release.
