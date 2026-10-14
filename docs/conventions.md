# Code conventions

## Programming principles

- Follow SOLID Principles
  - `S` - A class, function or component should have only one reason to change, meaning it should perform a single job.
  - `O` - Software entities should be open for extension, but closed for modification.
  - `L` -  Subtypes must be completely substitutable for their base types without breaking program behavior.
  - `I` -  Clients should not be forced to depend on methods or interfaces they do not use.
  - `D` - High-level modules should depend on abstractions (interfaces), not low-level concrete implementations.
- Every function must have single return statement


## Kotlin Naming Rules

A quick reference to the naming conventions from the official Kotlin coding conventions.
Source: <https://kotlinlang.org/docs/coding-conventions.html#naming-rules>

---

### Packages

- All lowercase, no underscores: `org.example.project`
- Prefer single-word segments. If you need several words, either join them (`org.example.myproject`) or use camel case (`org.example.myProject`).

### Classes and objects

Use **UpperCamelCase**.

```kotlin
open class DeclarationProcessor { /*...*/ }

object EmptyDeclarationProcessor : DeclarationProcessor() { /*...*/ }
```

### Functions, properties, local variables

Start with a lowercase letter and use **lowerCamelCase**, with no underscores.

```kotlin
fun processDeclarations() { /*...*/ }
var declarationCount = 1
```

### Class-like functions (exceptions)

Two kinds of functions, usually top-level, follow the *class* naming style instead:

**1. Factory functions** can share the name of the abstract type they return.

```kotlin
interface Foo { /*...*/ }

class FooImpl : Foo { /*...*/ }

fun Foo(): Foo = FooImpl()
```

**2. `@Composable` functions that return `Unit`**

```kotlin
@Composable
fun TabHeader() { /*...*/ }
```

### Properties

| Kind | Style | Example |
|---|---|---|
| Constants: `const val`, or top-level/object `val` with no custom getter holding deeply immutable data | SCREAMING_SNAKE_CASE | `const val MAX_COUNT = 8` |
| Top-level/object properties holding mutable data or objects with behavior | lowerCamelCase | `val mutableCollection: MutableSet<String> = HashSet()` |
| References to singleton objects | Same as `object` names (UpperCamelCase) | `val PersonComparator: Comparator<Person> = ...` |
| Enum constants | SCREAMING_SNAKE_CASE or UpperCamelCase, depending on usage | `enum class Color { RED, GREEN }` |

```kotlin
const val MAX_COUNT = 8
val USER_NAME_FIELD = "UserName"
```

### Backing properties

When a private implementation property and a public API property represent the same thing, prefix the private one with an underscore.

```kotlin
class C {
    private val _elementList = mutableListOf<Element>()

    val elementList: List<Element>
        get() = _elementList
}
```

> Android tip: this is the familiar `_uiState` / `uiState` pattern in ViewModels.

### Choosing good names

- **Classes:** a noun or noun phrase describing what the class is, e.g. `List`, `PersonReader`.
- **Methods:** a verb or verb phrase describing what the method does, e.g. `close`, `readPersons`.
- **Mutating vs. returning a copy:** the name should signal which one it is. For example, `sort()` changes the collection in place, while `sorted()` returns a new sorted copy.
- **Avoid meaningless words** such as `Manager` or `Wrapper`. The name should make the purpose clear.

#### Acronyms

| Acronym length | Rule | Examples |
|---|---|---|
| 2 letters | Both uppercase | `IOStream` |
| 3+ letters | Only the first letter capitalized | `XmlFormatter`, `HttpInputStream` |

---

### Cheat sheet

| Element | Convention | Example |
|---|---|---|
| Package | lowercase | `org.example.project` |
| Class / object / interface | UpperCamelCase | `DeclarationProcessor` |
| Function / variable / property | lowerCamelCase | `processDeclarations()` |
| Composable (returns `Unit`) | UpperCamelCase | `TabHeader()` |
| Factory function | Name of the returned type | `fun Foo(): Foo` |
| Constant | SCREAMING_SNAKE_CASE | `MAX_COUNT` |
| Enum constant | SCREAMING_SNAKE_CASE or UpperCamelCase | `RED` / `Red` |
| Backing property | `_` + lowerCamelCase | `_elementList` |
| Test method | Backticks with spaces or underscores allowed | `` `ensure everything works` `` |