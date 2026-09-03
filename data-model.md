# Money Counter — Data Model

## 1. Purpose

Define the minimum data structures required by the MVP.

The model is intentionally small. No relational database is required.

## 2. Denomination

Conceptual representation:

```text
Denomination
├── id: String
└── value: Long
```

### ID

The ID must remain stable when the denomination value is edited.

Example:

```text
id = "d1"
value = 5000
```

If edited:

```text
id = "d1"
value = 5500
```

The ID remains `d1`.

A UUID may be used, but deterministic simple IDs are also acceptable if the implementation guarantees uniqueness.

## 3. Persisted Configuration

JSON:

```json
{
  "version": 1,
  "denominations": [
    {
      "id": "d1",
      "value": 5000
    },
    {
      "id": "d2",
      "value": 2000
    },
    {
      "id": "d3",
      "value": 1000
    }
  ]
}
```

The array order defines display order unless the implementation chooses an explicit `position` field.

## 4. Validation

A persisted denomination is valid when:

```text
id is non-empty
value > 0
IDs are unique
values are unique
```

A persisted configuration is valid when:

```text
version is supported
denominations is present
every denomination is valid
```

If validation fails, use the default configuration.

## 5. Runtime Count State

The active count does not need to be persisted for MVP.

Conceptually:

```text
CounterState
├── targetAmount: Long?
├── quantities: Map<String, Long>
└── calculated result
```

The quantity map uses denomination ID:

```text
"d1" -> 10
"d2" -> 5
"d3" -> 20
```

This is safer than using denomination value as the map key because denomination values can be edited.

## 6. Derived Result

```text
CounterResult
├── countedTotal: Long
├── remaining: Long
├── excess: Long
└── status: CounterStatus
```

Prefer deriving these values from the current state rather than storing mutable duplicate values.

## 7. Status

```text
enum CounterStatus {
    EMPTY,
    COUNTING,
    COMPLETED,
    OVER
}
```

## 8. Persistence Boundary

Only the denomination configuration is persisted.

```text
Persisted
└── DenominationConfiguration

Not persisted for MVP
├── targetAmount
├── quantities
├── countedTotal
├── remaining
├── excess
└── status
```

This keeps the first version simple and avoids confusing an old unfinished count with a new counting session.

## 9. Versioning

The JSON configuration includes:

```json
"version": 1
```

Future schema changes should increment the version and use explicit migration logic if needed.

Do not silently reinterpret incompatible data.
