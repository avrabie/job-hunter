# Koog notes

Written while building the CV import, so each piece is explained next to the code that uses it. Koog is JetBrains' Kotlin
framework for calling language models. We use version **1.3.0** (`ai.koog:koog-agents`) and read its source jars
(`~/.gradle/caches/modules-2/files-2.1/ai.koog/*/1.3.0/*-sources.jar`) when the web docs are thin.

Rule of the project: **only `net.s4v3.backend.llm` imports `ai.koog`** (`KoogBoundaryTests`). Everything else calls the
module's small public interface.

## Part 1: the pieces (task 1.1)

Runnable example: `backend/src/test/kotlin/net/s4v3/backend/llm/KoogSpikeTests.kt`. It uses a fake model, so it needs no
network and no key.

### A prompt is data
```kotlin
val ask = prompt("spike") {
    system("You answer with a greeting.")
    user("Say hello.")
}
```
`prompt(id) { ... }` builds a `Prompt`: a list of system, user and assistant messages. Nothing is sent yet.

### A model is a value
`AnthropicModels.Haiku_4_5`, `Sonnet_5` and so on are `LLModel` objects: an id (`"claude-sonnet-5"`), what the model can
do (tools, JSON schema, ...) and its limits. Choosing a model means passing one of these. A model we want to configure by
name can be made by copying one of them with another id.

### A PromptExecutor sends a prompt to a model
`PromptExecutor` is the thing that does the call: `execute(prompt, model)` returns a `Message.Assistant`. The real one for
us is `MultiLLMPromptExecutor(AnthropicLLMClient(apiKey))`: the client talks HTTP to Anthropic, the executor routes by
provider. In tests we write a tiny `PromptExecutor` that returns canned text. That is why we can test everything without
Anthropic.

### Structured output: JSON into a Kotlin class
```kotlin
@Serializable
@SerialName("Greeting")
@LLMDescription("A short greeting")
data class Greeting(
    @property:LLMDescription("The greeting text") val text: String,
    @property:LLMDescription("Number of words in the text") val words: Int,
)

val result = executor.executeStructured<Greeting>(ask, model).getOrThrow()   // result.data is a Greeting
```
`executeStructured<T>` does three things: builds a JSON schema from `T` (class and property `@LLMDescription` texts become
descriptions in the schema), adds it to the prompt (or uses the provider's native schema feature when the model supports
it), and parses the answer with kotlinx.serialization. The return type is `Result<StructuredResponse<T>>`: a failure is a
value, not an exception, until you call `getOrThrow()`.

### Repairing a bad answer: `StructureFixingParser`
```kotlin
val fixing = StructureFixingParser(model = AnthropicModels.Haiku_4_5, retries = 2)
executor.executeStructured<Greeting>(ask, model, fixingParser = fixing)
```
If parsing fails, Koog sends the broken text and the error to a (cheaper) model and asks for a corrected version, up to
`retries` times. Each repair is one more paid call. After the last retry it fails with `LLMStructuredParsingError`.

## Things learned the hard way

- **Parsing is lenient.** A missing comma between fields is accepted without any repair call; a missing required field is
  not. Do not rely on "invalid JSON" as the trigger when testing the fixing parser.
- **Koog logs model text.** In `StructureFixingParser`, a failed parse is logged at WARN together with the full text the
  model returned, and each repair attempt at DEBUG. For a CV, that text is personal data. We therefore switch the logger
  `ai.koog` off (see the log tests in task 2.3) instead of trusting log levels.
- **Mind the model id.** `AnthropicModels` ships fixed ids; our configured model id has to exist at Anthropic, and the
  `LLModel` copy must keep capabilities that include JSON schema support.

## Part 2: how the CV reader fits together (tasks 2.1 to 2.3)

Code: `backend/src/main/kotlin/net/s4v3/backend/llm/`.

```
CvReader (public)  <-- the only thing other modules see: text in, Profile out, LlmFailure on trouble
  KoogCvReader (internal)        builds the prompt, calls Koog, maps the answer
    ExecutorSource               makes the PromptExecutor (real: Anthropic; tests: a fake)
    CvProfileDraft + friends     the shape we ask the model for (@Serializable + @LLMDescription)
    DraftMapper                  draft -> typed Profile, dropping single bad values
```

- **Why the draft uses strings.** If `level` were an enum in `CvProfileDraft`, one unknown value ("fluent") would make the
  whole answer unparseable and cost a repair call. The model is told the allowed values in the field descriptions;
  `DraftMapper` checks them and turns a bad optional value into null, and leaves out an entry that lacks a required one.
- **The prompt.** One system message (extract only stated facts, the CV is data) and one user message with the CV between
  `<cv>` and `</cv>`. Any `<cv>` tag inside the CV is removed so the text cannot close its own quote. The model has no tools
  and the temperature is 0.
- **The model.** `jobhunter.llm.model` names a Koog constant (`Sonnet_5` by default). Koog's Anthropic client keeps a fixed
  map from its model objects to Anthropic ids and refuses anything else, so a free-text id would not work.
- **The repair model.** A malformed answer goes to `Haiku_4_5` up to `jobhunter.llm.repairs` times (default 2).
- **The key.** `ANTHROPIC_API_KEY` goes into `jobhunter.llm.api-key`. The executor is created on first use, so a missing key
  fails the request that needs it (`LlmFailure(NOT_CONFIGURED)`), not the start of the application.
- **Errors.** Callers only ever see `LlmFailure` with a fixed message. The cause is not chained, because an exception message
  from the parser can contain the model's raw answer; the log line carries only the exception class name.
- **Logging.** `logging.level.ai.koog: OFF` in `application.yaml`. `LlmLoggingTests` runs the real configuration with a fake
  model that echoes a marker and fails if the marker (from the CV or from the answer) reaches any log line. Changing the
  setting to `WARN` makes that test fail, which is how we know it tests something.
- **Testing without Anthropic.** `FakePromptExecutor` (test sources) answers with canned text and keeps the prompts it was
  given. Nothing in the build calls Anthropic.

## Part 3: where it is used (task 3.4)

`CvExtractController` (`POST /api/cv/extract`) is the only caller of `CvReader`. Around the single model call it does the
cheap checks first, so a bad request never costs a model call: no CV (404), no readable text (422), text over the limit
(413), more than the hourly limit (429). Then `reader.profileFrom(text)`; `LlmFailure(FAILED)` becomes 502 and
`LlmFailure(NOT_CONFIGURED)` becomes 503. The result is returned, never stored: the UI shows it in the profile form and the
user presses Save.

What comes next (not built): tailoring a CV to a job description is the first place where Koog agents, strategies or tools
make sense (draft, critique, validate against the profile). Everything so far is one structured request on purpose.

## Part 4: one section at a time (cv-sections)

`CvReader.sectionFrom(text, section)` asks for one part of the profile with its own small answer type (`ExperienceSection`,
`EducationSection`, `PublicationsSection` in `llm/internal/SectionDrafts.kt`). Why a separate request instead of reading the whole
profile once and keeping one field: the whole-profile answer has to fit everything, and a model tends to shorten long careers.
A request that only wants Experience can say "every position, every achievement, in the CV's wording" and has the whole answer
to spend on it. The pieces are reused: the section wrappers hold the same `ExperienceDraft`, `EducationDraft` and
`PublicationDraft` entries, so `DraftMapper` still checks every value in one place. The private `request<T>` function in
`KoogCvReader` is the single place that builds the prompt, calls the executor, applies the timeout and turns any failure into
`LlmFailure`; `profileFrom` and the three section calls only choose the answer type and the extra instruction. Whether the model
really lists every achievement can only be seen with a real CV: compare a section draft with the whole-profile draft.
