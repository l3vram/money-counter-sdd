# Plan 003: History list screen, detail screen, and PDF export

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving to the next step. If anything
> in the "STOP conditions" section occurs, stop and report — do not improvise. This plan
> depends on Plans 001 and 002.
>
> **Drift check (run first)**: `git status --short` — confirm the files from Plans 001/002
> exist, and review the current `MainActivity.kt` and `MoneyCounterScreen.kt` against the
> excerpts below before editing.

## Status

- **Priority**: P1
- **Effort**: L
- **Risk**: MED
- **Depends on**: plans/historial-2026/001-saved-count-domain-repository.md, plans/historial-2026/002-history-viewmodel-save-button.md
- **Category**: feature
- **Planned at**: commit `210ca98`, 2026-09-03

## Why this matters

Completes the historial feature: the user can browse all saved counts, see a summary list,
open a detail view showing date + total + per-denomination lines, delete entries, and export
the detail as a PDF. This is the full user-facing surface of the history feature.

## Current state

- `app/src/main/java/com/moneycounter/MainActivity.kt` — `MoneyCounterApp()` uses
  `var currentScreen by remember { mutableStateOf("counter") }` and a `when` block to switch
  between `"counter"` and `"settings"`. No navigation library.
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` — counter screen.
  Its `TopAppBar` today shows a `Settings` `IconButton` in `actions`. It uses
  `formatMoneyBigDecimal(BigDecimal)` from `com.moneycounter.ui.components` (in
  `DenominationRow.kt`) for currency formatting, e.g. `$${formatMoneyBigDecimal(x)}`.
- `app/src/main/java/com/moneycounter/ui/components/DenominationRow.kt` — has top-level
  `formatMoney(value: Long): String` and `formatMoneyBigDecimal(value: BigDecimal): String`
  functions (currency formatting: thousands separators, comma decimals).
- From Plan 002, `MoneyCounterViewModel` now exposes `uiState.history: List<SavedCount>`,
  plus functions `saveCount()`, `deleteSavedCount(id)`.
- `app/src/main/java/com/moneycounter/domain/SavedCount.kt` and `SavedCountItem.kt` — from Plan 001.

Recon facts:
- Build: `./gradlew assembleDebug --console=plain`
- Compile check: `./gradlew compileDebugKotlin --console=plain`
- API level 26+; `android.graphics.pdf.PdfDocument` is available (API 19+). No extra dependency needed for PDF.
- The repo already depends on `androidx.compose.material:material-icons-extended` (all material icons available, including `Icons.AutoMirrored.Filled.ArrowBack`, `Icons.Default.History`, `Icons.Default.IosShare`/`Share`).

## Commands you will need

| Purpose   | Command                           | Expected on success |
|-----------|-----------------------------------|---------------------|
| Compile   | `./gradlew compileDebugKotlin --console=plain` | BUILD SUCCESSFUL |
| Build     | `./gradlew assembleDebug --console=plain`      | BUILD SUCCESSFUL |
| Tests     | `./gradlew test --console=plain`  | BUILD SUCCESSFUL; all pass |

## Scope

**In scope** (only these files — create the new ones):
- `app/src/main/java/com/moneycounter/MainActivity.kt` (edit — add navigation routes + history button)
- `app/src/main/java/com/moneycounter/ui/screens/MoneyCounterScreen.kt` (edit — add History icon to TopAppBar actions)
- Create `app/src/main/java/com/moneycounter/ui/screens/HistoryScreen.kt`
- Create `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt`
- Create `app/src/main/java/com/moneycounter/util/PdfExporter.kt`

**Out of scope** (do NOT touch):
- `DenominationManagementScreen.kt` (existing settings screen) — leave as-is.
- The `MoneyCounterViewModel` (already done in Plan 002).
- Any domain/calculator files.

## Git workflow

- Edit in place in the current working tree (this run does not use worktrees).
- Do NOT commit unless the operator explicitly asks.

## Steps

### Step 1: Create `PdfExporter.kt` — native PDF generation + share

File: `app/src/main/java/com/moneycounter/util/PdfExporter.kt`

A `class PdfExporter(private val context: Context)` with a method that renders a
`SavedCount` to a multi-page `PdfDocument`, writes it to a temp file, and launches an
Android share intent so the user can save/send it.

Signature:

```kotlin
class PdfExporter(private val context: Context) {
    fun export(saved: SavedCount)
}
```

Implementation outline:
1. Create `PdfDocument().apply { }`. If `saved.items` is empty, still render a header + "No denominations" line.
2. Page info: `PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber)` with
   `PdfDocument.PageInfo.Builder(595, 842, 0).create()` (A4 in points).
3. Use `android.graphics.Paint` to draw title text (bold, ~18sp), a "Fecha: <formatted date>"
   line (~12sp), "Monto total: $<target>" (~14sp bold), then each item line
   `"$<denomination> - <quantity> - $<subtotal>"`, wrapping/handling page overflow by starting
   a new page when `y > pageHeight - margin`.
4. Reuse the app's currency formatting. The functions `formatMoney`/`formatMoneyBigDecimal`
   are top-level in `com.moneycounter.ui.components.DenominationRow.kt` and are public — import them.
5. Format the date: `SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(saved.savedAt))`.
6. After finish: write PDF bytes to a temp file:
   `File(context.cacheDir, "reporte_${saved.id}.pdf")`, `FileOutputStream(...).apply { write(pdf.toByteArray? ) }` — note `PdfDocument` has no `toByteArray`; instead render each page to a `Bitmap`/`FileOutputStream` via `startPage`... simpler: use `document.finishDocument(fos)` writing directly to the file output stream.
7. Launch share:
```kotlin
val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
    putExtra(Intent.EXTRA_STREAM, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
context.startActivity(Intent.createChooser(intent, "Exportar reporte"))
```

**FileProvider**: You must add a `<provider>` to `AndroidManifest.xml` and a
`res/xml/file_paths.xml`. However `AndroidManifest.xml` is a NEW edit in scope for this plan.
Steps:
- In `AndroidManifest.xml` `<application>` add:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```
- Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
```
- Use `androidx.core.content.FileProvider` (already available via `androidx.core` dependency).

For a clean signature that lets callers react, prefer returning `(File, Uri)` from a
`buildPdf(saved): File` helper and a separate `share(file)` — the `export` method can do both.
Test this compiles.

### Step 2: Modify `MoneyCounterScreen.kt` to add the History icon to the TopAppBar

In the `MoneyCounterScreen`'s `TopAppBar` `actions` block (currently only the Settings
`IconButton`), add a History icon BEFORE the Settings icon:

```kotlin
actions = {
    IconButton(onClick = onNavigateToHistory) {
        Icon(
            Icons.Default.History,
            contentDescription = "Ver historial",
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
    IconButton(onClick = onNavigateToSettings) { ... existing ... }
}
```

Add `onNavigateToHistory: () -> Unit` to the `MoneyCounterScreen` composable parameter list.
Import `Icons.Default.History`.

**Verify**: `./gradlew compileDebugKotlin --console=plain` → BUILD SUCCESSFUL.

### Step 3: Update `MainActivity.kt` navigation

Change the `when` block to also handle `"history"`, and pass `onNavigateToHistory` into
`MoneyCounterScreen`:

```kotlin
var currentScreen by remember { mutableStateOf("counter") }
var selectedHistoryId by remember { mutableStateOf<String?>(null) }

when (currentScreen) {
    "counter" -> MoneyCounterScreen(
        viewModel = viewModel,
        onNavigateToSettings = { currentScreen = "settings" },
        onNavigateToHistory = { currentScreen = "history" }
    )
    "settings" -> DenominationManagementScreen(
        viewModel = viewModel,
        onNavigateBack = { currentScreen = "counter" }
    )
    "history" -> HistoryScreen(
        viewModel = viewModel,
        onNavigateBack = { currentScreen = "counter" },
        onOpenDetail = { id -> selectedHistoryId = id; currentScreen = "detail" }
    )
    "detail" -> selectedHistoryId?.let { id ->
        HistoryDetailScreen(
            viewModel = viewModel,
            savedCountId = id,
            onNavigateBack = { currentScreen = "history" }
        )
    }
}
```

Import `HistoryScreen`, `HistoryDetailScreen` from `com.moneycounter.ui.screens`.

### Step 4: Create `HistoryScreen.kt` — summary list + delete

File: `app/src/main/java/com/moneycounter/ui/screens/HistoryScreen.kt`

- TopAppBar with back arrow (`Icons.AutoMirrored.Filled.ArrowBack`, contentDescription "Volver")
  title "Historial".
- `LazyColumn` over `uiState.history` (collect from the ViewModel):
  - Each item: a `Card` showing `formatMoneyBigDecimal(saved.targetAmount)` (the "Monto total")
    and the formatted date (reuse the same SimpleDateFormat pattern as the PDF exporter).
    Example line: `"$5.000,00"` and `"03/09/2026 14:30"`.
  - Tap a card → `onOpenDetail(saved.id)`.
  - A delete action per item: an `IconButton` with `Icons.Default.Delete` tinted `error`
    that opens a confirm `AlertDialog` ("¿Eliminar este registro?") → calls
    `viewModel.deleteSavedCount(id)`.
  - Track "which item's delete dialog is open" with a `var pendingDeleteId by remember { mutableStateOf<String?>(null) }`.
- Empty state: if history is empty, show a centered `Text` "Aún no hay registros guardados".

Signature:

```kotlin
@Composable
fun HistoryScreen(
    viewModel: MoneyCounterViewModel,
    onNavigateBack: () -> Unit,
    onOpenDetail: (String) -> Unit
)
```

### Step 5: Create `HistoryDetailScreen.kt` — detail view + export button

File: `app/src/main/java/com/moneycounter/ui/screens/HistoryDetailScreen.kt`

- TopAppBar with back arrow and title "Detalle".
- On screen entry, resolve the `SavedCount` from `uiState.history.firstOrNull { it.id == savedCountId }`.
  If null, show a "Registro no encontrado" message and a back button.
- Body (a `Column`/`LazyColumn`):
  - "Fecha: <dd/MM/yyyy HH:mm>" (formatted from `savedAt`).
  - "Monto total: $<targetAmount>".
  - For each item in `saved.items`: a row `"$<denominationValue> - <quantity> - $<subtotal>"`,
    e.g. `$500 - 1 - $500,00`. Render only items (the repo already filtered zero-quantity on load).
  - Header labels: "DENOMINACIÓN  -  CANTIDAD  -  TOTAL".
- An export button (`FilledTonalButton`, label "EXPORTAR PDF", icon `Icons.Default.IosShare` or
  `Share`). On tap, `PdfExporter(context).export(saved)`.

To get `Context`: `val context = LocalContext.current` in the composable.

Signature:

```kotlin
@Composable
fun HistoryDetailScreen(
    viewModel: MoneyCounterViewModel,
    savedCountId: String,
    onNavigateBack: () -> Unit
)
```

**Verify after steps 2–5**: `./gradlew assembleDebug --console=plain` → BUILD SUCCESSFUL.

## Test plan

- No new unit tests (UI + PDF; verify by build).
- Existing test suite must stay green.

## Done criteria

- [ ] `./gradlew compileDebugKotlin --console=plain` exits 0
- [ ] `./gradlew assembleDebug --console=plain` exits 0
- [ ] `./gradlew test --console=plain` exits 0
- [ ] History icon appears in the counter TopAppBar next to Settings
- [ ] `HistoryScreen` lists saved counts (total + date), tappable to detail, with per-item delete + confirm dialog
- [ ] `HistoryDetailScreen` shows date, total, per-item lines, and an "EXPORTAR PDF" button that produces a shareable PDF
- [ ] `AndroidManifest.xml` has the FileProvider `<provider>` and `res/xml/file_paths.xml` exists
- [ ] No files outside the in-scope list are modified
- [ ] `plans/README.md` status rows updated

## STOP conditions

Stop and report back (do not improvise) if:

- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.
- You discover `PdfDocument` API differences that block the export approach (e.g. needing to
  render to a bitmap buffer first — if so, switch to the "draw each page to a `Bitmap` via
  `page.canvas` then store the PDF's `PageInfo` and call `document.finishPage(page)` after
  writing with an `OutputStream` wrapping — report which approach you took).
- FileProvider manifest merge fails (if `${applicationId}` substitution doesn't resolve in the
  authority in a non-module config — if so, hardcode `com.moneycounter.fileprovider`).
