# Plan 003: Rename the app to El Luiso and build the monogram launcher icon

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/el-luiso-redesign/README.md`.
>
> **Drift check (run first)**: `git diff --stat 999dec4..HEAD -- app/src/main/res app/src/main/AndroidManifest.xml`
> If anything under `app/src/main/res` or `AndroidManifest.xml` changed since
> commit `999dec4`, compare excerpts against live code; on a mismatch treat it
> as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW (manifest label + XML vectors + strings; no code behavior)
- **Depends on**: none
- **Category**: branding
- **Planned at**: commit `999dec4`, 2026-09-07

## Why this matters

Identity first: the launcher label must read **"El Luiso"** (design kit
`BRAND.md`) and the icon must be a recognizable green "L" monogram — the kit's
priority concept is "Inicial 'L' estilizada". Doing this alongside plan 001
(independent file sets) gives the branch an on-brand shell while screens are
reskinned in 004–007. The tagline "El que resuelve todo." is the brand's main
line and belongs in the iconography/strings as a first-class string.

## Current state

- `app/src/main/res/values/strings.xml` line 3:
  `<string name="app_name">Contador de dinero</string>` — the only string
  resource in the app (everything else is hardcoded in Kotlin, which the
  copy plan 008 addresses).
- `app/src/main/AndroidManifest.xml` line 7:
  `android:label="@string/app_name"` — already resource-driven.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — a white "M"-like
  vector on transparent, 108dp canvas:
  `<path android:fillColor="#FFFFFF" android:pathData="M54,30 L74,58 L62,58 L62,78 L46,78 L46,58 L34,58 Z" />`.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and
  `ic_launcher_round.xml` reference `@drawable/ic_launcher_foreground` and a
  background (adaptive icon). Legacy PNG mipmaps (`mipmap-*/`) also exist.
- No `ic_launcher_background.xml` vector — background is likely a color
  reference or a drawable.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Compile | `./gradlew compileDebugKotlin --console=plain` | exit 0 |
| Assemble (icon/label shipped) | `./gradlew assembleDebug --console=plain` | exit 0, APK produced |
| Grep | `grep -rn 'Contador de dinero' app/src/main` | no matches after step 2 |

## Scope

**In scope**:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml` (create if absent — launcher background color)
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml` (create)
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` (only if they need a color-reference update)
- `app/src/main/res/xml/` (only if a legacy icon path references the old drawables — verify first)

**Out of scope** (do NOT touch):
- Any Kotlin file — MainActivity bottom-nav restyle and screen copy land in
  later plans (003 is identity shell only). If you think Kotlin needs a change,
  STOP and report.
- `app/build.gradle.kts` / `build.gradle.kts` — no `versionName`/label logic to change.
- Legacy `mipmap-*` PNGs — adaptive icons on API 26+ take precedence; leave them.

## Git workflow

- Worktree branch created by orchestrator: `exec/003-rpt`.
- One commit, conventional: `feat(branding): rename app to El Luiso and add monogram icon`. Do NOT push.

## Steps

### Step 1: Rename the launcher label

In `app/src/main/res/values/strings.xml` replace the `app_name` value:

```xml
<string name="app_name">El Luiso</string>
```

Add the brand tagline (used by later copy work and the top bars):

```xml
<string name="tagline">El que resuelve todo.</string>
```

**Verify**: `grep -rn 'Contador de dinero' app/src/main` → no matches; then
`./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 2: Launcher background

Create `app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="el_luiso_background">#0F5132</color>
    <color name="el_luiso_yellow">#FACC15</color>
</resources>
```

Create `app/src/main/res/drawable/ic_launcher_background.xml`:
a solid-color adaptive background. Simplest and safe:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/el_luiso_background" />
</shape>
```

**Verify**: `./gradlew compileDebugKotlin --console=plain` → exit 0.

### Step 3: Redraw the monogram foreground

Replace the path in `app/src/main/res/drawable/ic_launcher_foreground.xml`
with a stylized "L" monogram on the 108dp canvas. Green-dark background is the
adaptive background; the FG draws the letter in cream/white with a yellow
accent. Use the current vector's structure (single `<vector>`, width/height
108, viewport 108). A clean "L":

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- vertical stem -->
    <path
        android:fillColor="#F8FAE5"
        android:pathData="M30,30 L46,30 L46,76 L76,76 L76,92 L30,92 Z" />
    <!-- yellow accent square (neutral corner) -->
    <path
        android:fillColor="#FACC15"
        android:pathData="M66,30 L82,30 L82,46 L66,46 Z" />
</vector>
```

Keep the byte dtype/rounding sane; verify visually in a launcher or just
robustness: the paths must be closed, filled, within the 108 box, and clear of
the adaptive safe-zone (~66dp inner circle). The letterplaces in the center of
the canvas; the accent sits in the top-right at the same relative area the old
icon had its top. Content description not needed for a vector drawable used as
launcher FG.

Update `mipmap-anydpi-v26/ic_launcher.xml` / `ic_launcher_round.xml` to point
at the new background if they don't already (they should keep
`android:foreground="@drawable/ic_launcher_foreground"` and set
`android:background="@drawable/ic_launcher_background"`).

**Verify**: `./gradlew assembleDebug --console=plain` → exit 0; APK builds
with the renamed label. Optionally `aapt dump badging` the APK to confirm
`application-label:'El Luiso'` (if aapt is available; not required).

### Step 4: Regression

**Verify**:
- `./gradlew test --console=plain` → exit 0
- `git status --short` → only the res/ files listed in Scope are touched.

## Test plan

No new tests (resources/vectors only). Regression: strings/kanji not used by
tests; `test` suite must still exit 0 (cover: no Kotlin change).

## Done criteria

All must hold:

- [ ] `strings.xml` `app_name` == `El Luiso`; `tagline` string present
- [ ] `grep -rn 'Contador de dinero' app/src/main` returns nothing
- [ ] `colors.xml` has `el_luiso_background` = `#0F5132`
- [ ] `ic_launcher_background.xml` exists; `ic_launcher_foreground.xml` redrawn as an "L" monogram (green background, cream/white L, yellow accent); adaptive icons reference both
- [ ] `./gradlew compileDebugKotlin` and `./gradlew assembleDebug` exit 0
- [ ] `./gradlew test` exit 0
- [ ] No Kotlin files modified
- [ ] `plans/el-luiso-redesign/README.md` status row for 003 → DONE

## STOP conditions

Stop and report (do not improvise) if:

- Existing icon files differ materially from the excerpts (another plan may
  have touched them).
- You find yourself needing to edit Kotlin or the manifest beyond the
  resource-driven label (which already points at `@string/app_name`).
- `aapt` not present — that's fine, skip the optional check.

## Maintenance notes

- Legacy `mipmap-*` PNGs are now stale visually but still used on API < 26 —
  acceptable for this milestone; the monogram replaces them when a designer
  exports raster. Do not ship half-generated PNGs in this plan.
- The tagline string is a seeds string for the copy pass (plan 008): top bars,
  empty states, and onboarding can pull `stringResource(R.string.tagline)`.
- If the user later supplies a mascot vector, it replaces the monogram's
  letterforms — the monogram's neutral design should survive as the app icon
  concept per `BRAND.md`.