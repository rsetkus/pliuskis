# Testing conventions

## Framework

- Kotest, MockK and Junit5, no other test framework.

## Story-scoped test naming (Sage convention)

Every test function name starts with the story it belongs to:

```kotlin
class testStory1ParsesSimpleFrontmatter() : FunSpec({})

class testStory3IndexSortsByDateDesc() : FunSpec({})
```

Why: Sage's Tester runs story-scoped via.
```gradle
./gradlew :app:testDebugUnitTest --tests "testStory<N>"
```

If a test legitimately covers behavior owned by multiple stories (rare; almost only at the integration/e2e boundary), prefix with the higher-numbered story and name the others in the docstring.

## Test commands

- **Story-scoped (Tester worker default):** `./gradlew :app:testDebugUnitTest --tests "testStory<N>"`
- **Full regression (EpicVerifier):** `./gradlew check`


## What kinds of tests to write

- **Unit:** small pure functions in isolation (front-matter parsing, slug derivation, date sort).
- **Integration:** one layer's public API against a fixture (e.g. a use case against fake data source).
