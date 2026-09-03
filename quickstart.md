# Money Counter — Quickstart Validation

## 1. Build

Open the Android project and verify:

```text
Build → Make Project
```

The project must compile without errors.

## 2. Launch

Start the app on an emulator or physical Android device.

Expected:

```text
Main counter screen
```

No login or network connection should be required.

## 3. Basic Count

Configure target:

```text
150000
```

Enter:

```text
5000 × 10
2000 × 8
1000 × 20
```

Expected total:

```text
50000
+16000
+20000
------
86000
```

Expected remaining:

```text
64000
```

## 4. Reach Target

Continue adding denominations until:

```text
countedTotal = targetAmount
```

Expected:

```text
$150.000
$150.000

MONTO COMPLETADO
```

## 5. Exceed Target

Add another denomination so:

```text
countedTotal > targetAmount
```

Expected:

```text
EXCEDENTE
+$X
```

## 6. Add Cuban Denomination

Open denomination management.

Add:

```text
250
```

Expected:

```text
250
```

appears in the configured list.

Close/reopen the app.

Expected:

```text
250
```

is still present.

## 7. Duplicate Validation

Try to add:

```text
250
```

again.

Expected:

```text
Esta denominación ya existe.
```

No duplicate is created.

## 8. Clear Count

Set a target and quantities.

Tap:

```text
BORRAR TODO
```

Expected confirmation.

Confirm.

Expected:

```text
target = empty/zero
all quantities = 0
denominations remain unchanged
```

## 9. Configuration Persistence

Add/edit/reorder denominations.

Close the application completely.

Launch again.

Expected:

```text
denomination configuration preserved
```

The active count does not need to be restored.

## 10. Corrupt Configuration

For development/testing only, simulate malformed `denominations.json`.

Launch app.

Expected:

```text
app does not crash
default denominations are loaded
```

## 11. Offline Test

Disable network connectivity.

Launch and use the application.

Expected:

```text
all MVP functionality works
```

No network request should be required.

## 12. Final Acceptance

Before MVP release:

```text
[ ] Build succeeds
[ ] Unit tests pass
[ ] Persistence tests pass
[ ] UI tests pass
[ ] Target works
[ ] Quantities work
[ ] Subtotals work
[ ] Total works
[ ] Remaining works
[ ] Completed state works
[ ] Excess state works
[ ] Add denomination works
[ ] Edit denomination works
[ ] Delete denomination works
[ ] Reorder works
[ ] Configuration survives restart
[ ] Clear-all works
[ ] Corrupt configuration is recovered safely
[ ] Offline mode works
```
