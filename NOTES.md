"Always read SPEC.md and NOTES.md before working."
"Fix only compile errors. Ignore warnings, including unused imports, unused variables and deprecations. Do not run extra cleanup passes."

# CodeAssist AI Plugin & Reference Notes

### 1. Plugin Setup & Identity
- **ID:** `ai-studio-mode`
- **Name:** `AI Studio Mode`
- **Description:** `Plan and build apps with your own AI`
- **Manifest:** `plugin/src/main/res/raw/codeassist_plugin.toml`
- **UI Entry Point:** `com.copilotenhanced.app.CopilotEnhancedUiPlugin`
  - Registers tool window `ai-studio-mode.studio`, titled `"AI Studio"`, icon `"sparkle"`, anchor `ToolWindowAnchor.LEFT`.
  - Renders `AiStudioScreen` with a top bar including a settings icon button (⚙) opening the in-plugin settings configuration dialog.
- **App Entry Point:** `com.copilotenhanced.app.PluginInfoActivity` (`plugin/src/main/AndroidManifest.xml`).

### 2. Main Classes in `reference/`
- **`agent-api`:**
  - `AgentWorkspace`: Port abstraction for project file I/O, search, symbols, diagnostics, and VM execution.
  - `AgentTool` / `AgentToolRegistry` / `ToolSpec` / `ToolArgs`: Tool contracts, schema builder, and execution results.
  - `AgentPermissionGate` / `PermissionMode` / `WriteRequest`: Permissions gating for mutating tool calls.
  - `AgentEvent` / `AgentEventSink`: Observable event stream for UI progress, streaming text, and tool status.
  - `LlmClient` / `LlmProvider` / `LlmRequest` / `LlmMessage` / `LlmStreamEvent`: Model-agnostic LLM client & stream types.
- **`agent-impl`:**
  - `AgentLoop`: Multi-turn orchestrator running the LLM generation + tool execution loop.
  - `OkHttpLlmTransport` / `LlmTransport`: OkHttp HTTP/SSE network transport with custom CA cert support.
  - `AnthropicProvider`, `OpenAiProvider`, `GeminiProvider`, `OpenRouterProvider`: Provider adapters translating to/from SSE.
  - `BuiltInTools`: Factory functions creating built-in workspace tools (`read_file`, `write_file`, etc.).
  - `ConversationCodec`: JSON serializer/deserializer for conversation messages.
  - `HistoryCompactor` & `RequestPacer`: Tool result summarizer and rate-limit pacer.
- **`agent-ui`:**
  - `AgentUiPlugin`: Registers chat drawer tool window (`agent.chat`) and permission overlay (`agent.permission`).
  - `ChatDrawer`: Main Compose chat UI rendering messages, thinking blocks, tool steps, and todos.
  - `AgentPermissionDialog`: App-wide modal dialog for approving/denying mutating tool writes in `ASK_EACH` mode.
  - `AgentHistorySheet`: Modal sheet listing saved chat sessions to resume or delete.
  - `AgentProvidersSheet`: Settings dialog to configure API keys, models, custom endpoints, and CA certs.

### 3. Calling the Model, Streaming Tokens & Progress
- **Model Invocation:** `AgentLoop.runTurns()` constructs `LlmRequest` and calls `LlmClient.chat(request)`. Implemented by provider (e.g. `AnthropicProvider.chat(...)`).
- **Token Streaming:** `LlmTransport.sse()` streams SSE strings; provider decoders (e.g. `AnthropicStreamDecoder.decode()`) emit normalized `LlmStreamEvent` items.
- **Progress Reporting:** `AgentLoop.Turn.consume(event, sink)` dispatches events via `AgentEventSink.emit()` (`AgentEvent.AssistantTextDelta`, `AssistantThinkingDelta`, etc.).

### 4. Tool Definitions & Tool Loop
- **Definition:** Built via `AgentTool` interface or `tool(name, description, parameters, summarize) { args -> ... }` in `BuiltInTools.kt`. Mutating tools set `isMutating = true`.
- **Execution Loop:** `AgentLoop.runTurns()` collects tool calls from `Turn.toolCalls()`. `AgentLoop.executeCalls()` runs read tools concurrently and mutating tools sequentially through `AgentPermissionGate`.

### 6. Settings & Provider Configuration
- **Settings Page:** `com.copilotenhanced.app.settings.AiStudioSettingsPage` (`SETTINGS_PAGE_EP`):
  - Base URL (key `baseUrl`, no hardcoded default)
  - API Key (key `apiKey`, never logged)
  - Model name (key `modelName`)
  - IntSlider "Temperature x10" (0 to 20, default 7, key `temperature`)
  - Action button "Test connection" (key `testConnection`): uses `OpenAiCompatibleClient` reading `ProviderConfigStore` at request time.
- **Provider Config & Store:**
  - `com.copilotenhanced.app.settings.ProviderConfig`: Data class holding `baseUrl`, `apiKey`, `modelName`, `temperatureX10`, with `temperature: Float` computed property.
  - `com.copilotenhanced.app.settings.ProviderConfigStore`: Helper loading `ProviderConfig` from `Context` (SharedPreferences) or `PreferenceReader` dynamically.
  - `testConnection`: Redirects to `OpenAiCompatibleClient.testConnection()` to maintain a single HTTP request path.
- **Plugin Registration:** `com.copilotenhanced.app.CopilotEnhancedPlugin` registers the settings page under `SETTINGS_PAGE_EP`.

### 7. LLM Client & SSE Streaming (`com.copilotenhanced.app.llm`)
- **Contracts:**
  - `ChatMessage(role: String, content: String)`: Conversation message model with helper constructors (`system`, `user`, `assistant`).
  - `LlmEvent`: Sealed interface representing stream items: `Delta(text)`, `Done`, `Error(message)`.
  - `LlmClient`: Interface with `fun stream(messages: List<ChatMessage>, system: String? = null): Flow<LlmEvent>`.
- **Implementations:**
  - `OpenAiCompatibleClient`: Single unified HTTP client targeting `{baseUrl}/chat/completions` with `stream=true`. Dynamically reads `ProviderConfigStore` at call time.
  - `OpenAiSseParser`: Decoder for SSE `data:` payloads extracting delta content, stream errors, and `[DONE]` signals.
  - `FakeLlmClient`: Test implementation of `LlmClient` recording requests (`recordedRequests`) and emitting configurable deltas or error events.
- **Unit Testing:** `OpenAiSseParserTest` covers delta extraction, SSE comments/keepalive filtering, done handling, embedded stream errors, URL normalization, and header validation.

### 8. Chat Storage & Repository (`com.copilotenhanced.app.chat`)
- **Data Models:**
  - `ChatMode`: Enum with `PLAN` and `BUILD` modes.
  - `Chat`: Data class holding `id`, `title`, `mode`, `messages: List<ChatMessage>`, `plan: String?`, `planVersion: Int`, `memory: String`, `createdAt: Long`, `updatedAt: Long`.
- **Storage Layout:**
  - Root directory: `reg.dataDir/chats/<id>/`
  - `chat.json`: Full chat metadata, mode, plan, planVersion, and message history.
  - `memory.md`: Persistent Markdown memory text (one memory file per chat).
- **`ChatRepository` API:**
  - `create(title, mode, id)`: Creates a new chat folder, writes initial `chat.json` and empty `memory.md`.
  - `list()`: Returns all chats sorted by `updatedAt` descending.
  - `get(id)`: Reads `chat.json` and syncs `memory.md` content into `Chat.memory`.
  - `append(id, message)`: Appends a `ChatMessage`, updates `updatedAt`, and writes `chat.json`.
  - `setMode(id, mode)`: Updates chat mode (`PLAN` / `BUILD`), updates `updatedAt`, and saves.
  - `setPlan(id, plan, version)`: Updates plan markdown and auto-increments or sets planVersion.
  - `readMemory(id)`: Reads `memory.md` for the given chat ID.
  - `writeMemory(id, text)`: Atomically writes `memory.md` and updates `chat.json`.
  - `rename(id, title)`: Renames chat title and updates `updatedAt`.
  - `delete(id)`: Recursively removes `chats/<id>/` folder and all chat assets.
  - **Atomic Writes:** Uses temporary file creation in target directory, flushes and syncs file descriptor, then executes atomic move/rename (`StandardCopyOption.ATOMIC_MOVE`).
- **Unit Testing:**
  - `ChatRepositoryTest`: Comprehensive unit test suite using temporary directories verifying CRUD, sorting, message appending, mode changes, plan revisions, memory syncing, and atomic error handling.

### 9. Chat Tool Window UI & Architecture (`com.copilotenhanced.app.ui`)
- **State Management:**
  - `ChatUiState`: Data class holding `currentChat`, `messages`, `isStreaming`, `streamingText`, `progressMessage`, `error`, `historyList`, `currentMode`, and active UI flags.
  - `ChatViewModel`: Wires `LlmClient` and `ChatRepository` with coroutine-driven streaming, message auto-titling, stream cancellation / stopping with partial message retention.
- **UI Components:**
  - `AiStudioScreen`: Root Composable coordinating `ChatTopBar`, `ChatMessageList`, `ChatComposer`, `ChatHistoryScreen`, and `AiStudioSettingsDialog`.
  - `ChatTopBar`: Top bar displaying the current chat title, New Chat button (`+`), History button (`🕒`), Plan/Build mode toggle chip, and Settings button (⚙).
  - `ChatMessageList`: LazyColumn message container with auto-scroll to latest tokens. Renders user bubbles (aligned right, primary color container) and assistant blocks (left-aligned, full-width Markdown).

### 10. Recent Updates (Build Stamp 2026-10-09-D)
- Pinned original plan approved message and latest real user message across requests, keeping old tool rounds trimmed first without dropping user messages.
- Appended `CURRENT USER REQUEST` (max 6000 chars) to system prompt.
- Added Fix mode system prompt handling for state DONE / BUILDING with approved plan.
- Allowed state transitions `DONE <-> BUILDING`, updated ModeController tests.
- Adjusted history compactor to protect `read_file` results within last 12 tool messages.
- Added compiler error prefixing for user messages containing `.kt:` and `error` / `e: `.

### 10. Skills Management & Integration (`com.copilotenhanced.app.chat.SkillStore`, `SkillsScreen`)
- **Storage & Parsing:**
  - `SkillStore`: Stores `.md` files in `reg.dataDir/skills/` (or `context.filesDir/ai-studio/skills`) and persists enable states in `skills_state.json`.
  - `Skill`: Parses optional YAML front-matter (`name`, `description`), falling back to file name.
- **Skills Screen (`ScreenContribution`):**
  - Registered under ID `"ai-studio-mode.skills"`.
  - Displays list of skills with name, description, enable switch, delete with confirmation dialog, tap to view text dialog, and "Import .md file" button.
  - Import uses system file picker when available; falls back to absolute file path dialog or markdown paste dialog and reports which method was used.
- **System Prompt Integration:**
  - Enabled skills are included in the system prompt as a short list (name + description only).
  - Full skill body is included only when the skill name appears in the user request or plan, capped at 3,000 characters per skill.
- **Access Points:**
  - Accessible via Skills button (`📜`) in the chat top bar (`ChatTopBar`) and Action control `"Manage skills"` on the settings page (`AiStudioSettingsPage`).

### 10. LLM Tool Calling Extension (Added)
- Added `ToolDef(name, description, parametersJsonSchema)` data class.
- Updated `LlmClient.stream` to accept optional `tools: List<ToolDef>`.
- `OpenAiCompatibleClient` builds `"tools"` array and `"tool_choice": "auto"` when `tools` is non-empty.
- `OpenAiSseParser` accumulates `delta.tool_calls` by index across chunks and emits `LlmEvent.ToolCall(id, name, argumentsJson)` and `LlmEvent.Done(finishReason)`.
- `ChatMessage` supports assistant tool calls (`tool_calls`) and tool results (`tool_call_id`).


### 10. Build Mode & Milestones Wiring
- **Proceed Flow:**
  - When user clicks Proceed on an approved plan, `ChatViewModel.proceedPlan()` transitions mode state (`APPROVED → BUILDING`), updates chat mode to `ChatMode.BUILD`, and starts a build request turn.
- **Build System Prompt (`ModeController`):**
  - Built from `prompts/build_mode.md` + approved plan (`chat.plan`) + enabled skills + chat memory (`chat.memory`) + a short project file tree (`{{FILE_TREE}}` generated via recursive file listing up to depth 3, excluding `.git`, `build`, etc.).
- **Milestone Checklist (`MilestoneChecklist`):**
  - Parses `<progress milestone="..." status="...">...</progress>` blocks into `MilestoneState` items (tracking milestone id, status `pending|started|done|failed`, and note).
  - Renders horizontal milestone status chips with distinct icons and colors above the composer in `AiStudioScreen`.
  - `MarkdownRenderer` (`MarkdownView`, `MarkdownParser`, `CodeBlockView`): Markdown parser and renderer handling headers (`#`, `##`, `###`), bold (`**`), italic (`*`), inline code (`` ` ``), and syntax-highlighted code blocks with Copy button.
  - `ChatMessageBubble`: Message bubble layouts for user messages, assistant markdown messages, and active streaming tokens with `BlinkingCursor`.
  - `ChatProgressLine`: Lightweight indicator displaying progress status messages (e.g. "Thinking...", "Writing file X...") with circular spinner.

### 12. GitHub Repository Skill Import (Added)
- **Import from Repository:** Added "Import from repo" button on the Skills screen.
- **GitHub Importer (`GitHubImporter`):** Parses GitHub URLs (`https://github.com/owner/repo` or `/tree/branch/path`), queries GitHub contents API (`api.github.com`) for candidate `SKILL.md` and `.md` files at repo root, `skills/*/SKILL.md`, and in the given path off the main thread (`Dispatchers.IO`).
- **Checklist & Storage:** Displays fetched files in a checklist dialog with selection. Imports selected files into `SkillStore` (`importMarkdownWithSource`) and persists source URLs in `skills_sources.json` for refreshing.
- **Error Handling:** Gracefully handles rate limits (403/429), 404 not found, and offline network errors with readable error messages.

- **UI Debug & Permission Card:**
  - Displays resolved project root in permission dialog and debug line under first build message along with tree entry count.
  - `ChatComposer`: Multi-line text input field supporting dynamic sizing up to 5 lines, Send button (`➤`, enabled when text is non-empty), and Stop button (`⏹`, active during generation).
  - `ChatHistoryDialog`: Modal dialog displaying previous chats with timestamps, mode badges, message counts, instant selection, deletion (`🗑`), and new chat creation.
  - `AiStudioSettingsDialog`: Settings configuration dialog for base URL, API key, model name, and temperature slider with connection testing.
- **Unit Testing:**
  - `ChatViewModelTest`: Tests chat creation, user message sending, live token streaming, streaming stop/interruption, chat switching, mode toggling, deletion, and error state transitions.

### 10. History Screen Contribution & Polish (`ChatHistoryScreen`)
- **Screen Registration:**
  - `CopilotEnhancedUiPlugin`: Registers `Screen(id = "ai-studio-mode.history", title = "History")` via `ui.screen(...)` in `UiRegistration`.
  - Seamlessly reachable both from the registered IDE screen and directly in the Tool Window via the History button (`🕒`) in `ChatTopBar`.
- **Architecture & Components:**
  - `ChatHistoryScreen`: Main history screen with back navigation, `+ New Chat` header action, search filtering with clear action, and state dispatching.
  - `HistoryChatCard`: 16dp rounded card displaying chat title, mode badge (`PLAN` / `BUILD`), last message preview (up to 2 lines, truncated with `...`), relative timestamp (`2m ago`, `Yesterday`, etc.), and delete icon action.
  - `HistoryDialogs` (`HistoryRenameDialog`, `HistoryDeleteDialog`): Modal dialogs for confirming chat renaming and destructive chat deletion.
  - `HistoryStates` (`HistoryLoadingState`, `HistoryErrorState`, `HistoryEmptyState`): Dedicated composables for async loading indicator, error message with Retry button, and empty search/chat state illustration.
  - `ChatHistoryLogic`: Pure helper object for filtering chats by title/content, computing relative time ago strings, and extracting formatted last message previews.
- **Interactions & Gestures:**
  - **Tap:** Switches to the selected chat and returns to the active conversation view.
  - **Long-Press:** Triggers options menu with Rename and Delete choices.
  - **Swipe (`SwipeToDismissBox`):** Swiping right triggers Rename; swiping left triggers Delete confirmation.
  - **Touch Targets & Accessibility:** All interactive controls (buttons, icons, cards) meet or exceed the 48dp touch target threshold, and all icons have descriptive content descriptions.
  - **Styling:** Adheres strictly to Material 3 theme colors (`colorScheme.*`), 4/8dp spacing grid, 16dp rounded cards, clear type hierarchy (`titleMedium`, `bodyMedium`, `labelSmall`).
- **Unit Testing:**
  - `ChatHistoryLogicTest`: Tests query filtering by title, query filtering by message body, empty query handling, nonexistent query handling, relative time ago string formatting, and last message preview extraction.

### 11. Mode Controller, Prompt Loader & State Machine (`com.copilotenhanced.app.chat`)
- **State Model (`ModeState`):**
  - States: `IDLE`, `CLARIFYING`, `PLAN_READY`, `REVISING`, `APPROVED`, `BUILDING`, `VERIFYING`, `DONE`, `REJECTED`.
  - `isBuildPhase`: Evaluates whether the state belongs to the build phase (`APPROVED`, `BUILDING`, `VERIFYING`, `DONE`).
- **Prompt Loading (`PromptLoader`):**
  - Pure Kotlin class loading templates from classpath via the plugin classloader (`PromptLoader::class.java.classLoader.getResourceAsStream("prompts/...")`).
  - Does not rely on Android assets or absolute filesystem paths.
  - Exposes `loadPlanPrompt()` (`prompts/plan_mode.md`), `loadBuildPrompt()` (`prompts/build_mode.md`), and parameterized `loadPrompt(path)`.
  - Supports custom classloaders for testing and raises `IllegalStateException` on missing prompt resources.
- **Skills (`Skill`):**
  - Model holding `name`, `description`, and `content`.
  - Markdown parser (`Skill.fromMarkdown`) with front-matter extraction (`name:`, `description:`).
  - Formatter rendering markdown blocks for injection into system prompts.
- **State Controller (`ModeController`):**
  - Enforces allowed transitions between states and throws `IllegalStateException` on illegal transitions.
  - Methods: `allowedTransitionsFrom(state)`, `canTransition(from, to)`, `canTransitionTo(target)`, `transition(to)`, `transition(from, to)`, `reset(state)`.
  - `systemPromptFor(state, chat, skills, memory)`: Resolves the appropriate system prompt (Plan mode vs. Build mode), performs runtime substitution for `{{PLAN}}`, `{{SKILLS}}`, `{{MEMORY}}`.

### 12. Fix Mode, Compiler Output Detection & Exception Handling (Latest)
- **Fix Mode & Tool Loop for Approved Plans / State DONE:** Chats with an approved plan or in state `DONE` run the tool loop (`startBuildLoop`) when sending messages.
- **Fix Mode System Prompt & History:** System prompt includes `build_mode.md`, the fix mode line, and a short list of files created in this chat. Sends only the last 6 messages (`takeLast(6)`).
- **Compiler Output Detection:** If a user message contains `.kt:` and either `error` or `e: `, a hidden instruction message `"These are compiler errors from the IDE build. Find the files and fix them."` is automatically prepended.
- **Mode Controller Transitions:** Allowed `DONE <-> BUILDING` transitions in `ModeController` and tests.
- **Exception & Empty Response Handling:** Exceptions are never swallowed (shown as error bubbles). Empty responses without text or tool calls show `"The model returned nothing"` with a Retry button.
  - Overloads support skills passed as `List<Skill>`, `List<String>`, and raw `String`.
- **Unit Testing (`ModeControllerTest`):**
  - Comprehensive unit test suite executed by `Main.kt`.

### 12. Build Tool Loop & Fake Tools (`com.copilotenhanced.app.tools`)
- **`ToolExecutor` & `FakeToolExecutor`:**
  - `ToolExecutor` interface defining `definitions(): List<ToolDef>` and `suspend fun execute(name: String, argsJson: String): String`.
  - `FakeToolExecutor` maintains in-memory files and directory structures, implementing `list_directory`, `read_file`, `write_file`, `create_directory`, and `delete_file` with short descriptive outputs.
- **Build Loop in `ChatViewModel` (`startBuildLoop`):**
  - Runs multi-turn LLM generation loops in `BUILDING` state up to 40 rounds.
  - Collects tool calls, executes them via `FakeToolExecutor`, appends tool results with `hidden = true`, and re-queries the LLM.
  - Sanitizes older `write_file` arguments in conversation history to `[file written, N lines]`.
  - Automatically sends hidden user message `"Continue with the next milestone."` (up to 10 times) if the model stops without tools while milestones remain.
- **UI Tool Call Rows (`ChatMessageBubble`):**
  - Renders tool calls as clean collapsed rows (`"✓ write_file app/Foo.kt"`), never exposing file contents in chat.
  - Verifies all 33 allowed transitions.
  - Systematically checks all 81 state pairs to guarantee illegal transitions are rejected with `IllegalStateException`.
  - Tests end-to-end workflows (happy path, revision cycle, reject & reset).
  - Tests prompt resolution and template substitutions for Plan Mode and Build Mode.
  - Tests `Skill` markdown parsing and `PromptLoader` classpath behavior.

### 13. History Compaction & Placeholder Validation
- **Compaction (`compactHistoryForLlm`):**
  - Applied only to the message history copy sent to the LLM.
  - Never modifies saved chat repository messages, ongoing execution arguments, or tool executors.
  - Compacts `write_file` contents to `(content omitted)` only in assistant messages older than the last 6 tool rounds.
  - Compacts `read_file` results to `(file was read earlier; read it again if you need it)` only in tool rounds older than the last 4 rounds.
- **Placeholder Validation (`write_file`):**
  - Rejects empty content, `(content omitted)`, or content starting with `[file written` with message `"Rejected: that is placeholder text, not file content. Send the full real file content."` and writes nothing.

### 12. Protocol Parser (`com.copilotenhanced.app.protocol`)
- **Data Models & Contracts:**
  - `ParsedPart`: Sealed interface with `Text`, `Questions` (holding parsed `QuestionsPayload` and raw text), `InvalidQuestions` (holding raw text of unparseable block), `Plan` (holding `title`, `version`, `content`), and `Progress` (holding `milestone`, `status`).
  - `QuestionOption`, `QuestionItem` & `QuestionsPayload`: Hold question items where options are objects with `emoji: String?`, `label: String`, and `description: String?`. Supports polymorphic serialization to decode both primitive strings (from legacy JSON) and object options.
- **Protocol Parser (`ProtocolParser`):**
  - Line-format `<questions>` protocol:
    - Questions start with `## Question text?`.
    - Type line is `single` or `multi`, optionally with `| custom` (`allowCustom`).
    - Option lines start with `- emoji Label | description` (emoji and description optional).
  - Forgiving parsing:
    - Ignores stray closing tags with no opener (strips `</questions>` and broken `</questions` from text).
    - Accepts missing closing tag at stream end on `flush()`.
    - Trims blanks and ignores unknown lines.
    - Falls back to old JSON form if line format is not matched or fails.
  - Unparseable block handling:
    - If a `<questions>` block cannot be parsed, produces `ParsedPart.InvalidQuestions`.
    - UI displays the raw text alongside a Retry action button.
    - Retry action sends a hidden user message: `"Your last <questions> block was invalid. Resend only the corrected block in the required format."`.
    - `ChatViewModel` automatically retries up to a maximum of 1 time when `InvalidQuestions` is encountered during streaming.
- **Unit Testing:**
  - `ProtocolParserTest`: Comprehensive unit test suite covering line format questions, multi-select and option variants (emoji, label, description), multiple questions in one block, forgiving blanks and unknown lines, stray closing tag stripping (including broken `</questions` before `<questions>`), missing closing tag at stream end, unparseable block handling, and legacy JSON fallback.

### 13. Plan Mode Phase 1 & Chat Flow Polish (`com.copilotenhanced.app.ui`)
- **Chat Flow Enhancements:**
  - **ParsedPart Fallback Rendering:** Any `ParsedPart` without a dedicated renderer (such as `Plan` and `Progress`) is displayed as raw text (`toRawText()`) in the assistant bubble rather than being dropped.
  - **Stream Buffer Flush:** `ProtocolParser.flush()` is called upon stream completion to retrieve and render any text or blocks held back in the parser buffer.
  - **Error & Empty Response Handling:** Requests ending in an error or an empty model response display an `ErrorMessageBubble` featuring a "Retry" button (`onRetry`) with >=48dp touch targets and Material 3 error styling.
- **Plan Mode Phase 1 & Question Cards:**
  - **ModeController Integration:** In `ChatMode.PLAN`, sending the first idea transitions `ModeController` from `IDLE` to `CLARIFYING` and uses the `plan_mode.md` system prompt dynamically resolved via `ModeController.systemPromptFor()`.
  - **QuestionCard UI (`QuestionCard.kt`):** Renders interactive question cards when `ParsedPart.Questions` is returned by `ProtocolParser`:
    - **State Design:** Full state model (`QuestionCardState`: `Loading`, `Empty`, `Error`, and `Content`) with dedicated composable renderers.
      - Loading: 16dp rounded card with `CircularProgressIndicator` and status text.
      - Empty: Informational vector icon with helpful text and continuation button.
      - Error: Material 3 `errorContainer` styling, error vector icon, error message, and a Retry button.
      - Content: Header with checklist icon, title, question count badge, selection guidance, option chips, custom answer input, and action buttons.
    - **Material 3 Theming:** Strictly adheres to Material 3 theme colors (`colorScheme.surface`, `primary`, `outlineVariant`, `errorContainer`, etc.) with zero hardcoded colors.
    - **Grid & Hierarchy:** Strict 4/8dp grid spacing, 16dp rounded cards, and clear typography hierarchy (`titleMedium`, `titleSmall`, `bodyMedium`, `bodySmall`, `labelLarge`, `labelSmall`).
    - **Touch Targets & Accessibility:** All interactive elements (FilterChip, buttons, text fields) meet or exceed 48dp touch targets (`heightIn(min = 48.dp)`). All vector icons include meaningful `contentDescription` attributes.
    - **Subtle Animations:** Seamless animations using `animateContentSize()` for dynamic content sizing and state transitions.
    - **Logic Separation:** Business logic (`formatAnswers`, `toggleOption`) extracted into standalone testable functions outside composables.
- **Unit Testing:**
### 14. Plan Mode Phase 2 & PlanCard (`com.copilotenhanced.app.ui`)
- **Protocol & State Transitions:**
  - When `ProtocolParser` yields `ParsedPart.Plan`, `ChatViewModel` saves the plan in the chat repository with its computed version and transitions `ModeController` to `ModeState.PLAN_READY`.
  - **Revise:**
    - Opens an inline revision comment box.
    - Submitting transitions state to `ModeState.REVISING` and sends `"Revise the plan: <comment>"`.
    - The new plan generated by the LLM increments the plan version (`version + 1`), saves it to the chat, and retains the prior plan message in conversation history.

### Step A Read-Only Report
1) **Places constructing `ProjectToolExecutor` or calling `ProjectTools`**:
   - `plugin/src/main/kotlin/com/copilotenhanced/app/tools/ProjectToolExecutor.kt`, line 6: `class ProjectToolExecutor(private val rootDir: File = File("."))` — default root passed is `File(".")`.
   - `plugin/src/main/kotlin/com/copilotenhanced/app/ui/ChatViewModel.kt`, line 784: `val toolExecutor = com.copilotenhanced.app.tools.ProjectToolExecutor()` — default constructor uses `File(".")`.
   - `plugin/src/main/kotlin/com/copilotenhanced/app/tools/ProjectTools.kt`, line 8: `fun findProjectRoot(startDir: File = File("."), context: android.content.Context? = null)` — default `startDir` is `File(".")`. Line 38 uses `File(".").absoluteFile`.
2) **Substitution of `{{TREE}}` and `{{CONVENTIONS}}`**:
   - Substituted in `ModeController.kt` (lines 153 & 154) when processing `prompts/build_mode.md`.
   - `{{TREE}}`: Filled with the project file tree generated via `getProjectFileTree(rootDir, maxDepth = 5, maxEntries = 300)`.
   - `{{CONVENTIONS}}`: Filled with the text of `PROJECT_CONVENTIONS.md` plus `"CURRENT <relative path>:"` and the text of every `module.toml` found (max 4,000 characters each).
3) **Existence of T7 changes**:
   - `resolveProjectRoot`: Does not exist as a dedicated validation function yet (currently `findProjectRoot` is used).
   - Probe file (`.aistudio_probe`): Exists in `ChatViewModel.kt`.
   - `[root=...]` prefix on tool errors: Does not exist yet in tool execution returns.

    - Transitions `ModeController` to `ModeState.APPROVED` (preparing for the upcoming Build phase).
    - Renders a "Plan Approved ✓" status badge.
  - **Reject:**
    - Transitions `ModeController` to `ModeState.REJECTED`.
    - Displays a "Plan Rejected" banner with helpful retry/revision hints.
- **PlanCard UI & Components (`PlanCard.kt` & `PlanCardLogic.kt`):**
  - **States:** Complete state model (`PlanCardState`: `Loading`, `Empty`, `Error`, `Content`).
    - Loading: 16dp rounded card with `CircularProgressIndicator` and status text.
    - Empty: Vector icon with helpful text.
    - Error: `errorContainer` card with error icon, message, and Retry button.
    - Content: Card with Plan icon, title, version badge (e.g. `v1`, `v2`), scrollable markdown body with collapsible sections, and three action buttons (`Revise`, `Proceed`, `Reject`).
  - **Collapsible Markdown Sections:**
    - `parsePlanSections()` splits Markdown plan text into logical sections based on headers (`#`, `##`, `###`), preserving intro content as `Overview`.
    - Each section features an interactive accordion header with smooth collapse/expand transitions (`AnimatedVisibility` and `animateContentSize()`).
  - **Material 3 Design & Accessibility:**
    - Built exclusively using Material 3 theme colors (`colorScheme.*`) with zero hardcoded colors.
    - 4/8dp grid spacing, 16dp rounded corners, and clear type hierarchy (`titleMedium`, `titleSmall`, `bodyMedium`, `labelLarge`, `labelMedium`).
    - Touch targets for all interactive controls (buttons, inputs, section toggles) are at least 48dp.
    - All vector icons specify descriptive `contentDescription` strings.
    - Separation of concerns: parsing and helper logic isolated in `PlanCardLogic.kt`.
- **Unit Testing:**
  - `PlanCardLogicTest`: Tests section parsing with headers, fallback parsing without headers, empty/blank markdown handling, revision prompt formatting, and plan version calculation.
  - `ChatViewModelTest`: Tests `PLAN_READY` transition on plan reception, plan persistence in `ChatRepository`, revision cycle with version incrementation and history preservation, proceed flow to `APPROVED`, and reject flow to `REJECTED`.
  - `ModeControllerTest`: Validates allowed transitions to/from `PLAN_READY`, `REVISING`, `APPROVED`, and `REJECTED`.

### 15. PlanCard Button Fixes & Vertical Stacking (BUG 1 & BUG 2)
- **Bug 1 Fix (Button Chains & State Persistence):**
  - Traced each button: `PlanCard` onClick (`onProceed`, `onReject`, `onRevise`) → `PlanContentState` → `PlanCardView` → `PlanCard` → `AssistantMessageBubble` / `StreamingMessageBubble` → `ChatMessageList` → `AiStudioScreen` → `ChatViewModel` (`proceedPlan()`, `rejectPlan()`, `revisePlan()`).
  - Identified that transition calls were failing if `modeController.currentState` wasn't correctly synchronized, and state wasn't persisted across app restarts.
  - Added `modeState: ModeState` to `Chat` data class and `ChatRepository.setModeState()`, ensuring `modeState` is saved in `chat.json` and correctly restored upon loading initial chat or switching chats.
  - Implemented proper error handling for illegal transitions: caught `IllegalStateException`, logged via `android.util.Log.e`, and displayed the error in the chat as a short error bubble (`_uiState.update { it.copy(error = e.message) }`).
  - Added logic to disable all three action buttons and display the chosen result banner (e.g. `✓ Approved`, `✗ Rejected`, `✎ Revising...`) once a button is used so it cannot be tapped twice.
- **Bug 2 Fix (Vertical Stacking & Layout):**
  - Stacked the three action buttons vertically at full width (`Modifier.fillMaxWidth()`): Proceed (filled, primary color), Revise (outlined), and Reject (text button, error color) in the specified order.
  - Enforced 48dp minimum height (`heightIn(min = 48.dp)`) and 8dp spacing (`Arrangement.spacedBy(8.dp)`).
  - Wrapped plan content sections in a scrollable container (`Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())`) keeping the plan text scrollable above the action buttons.

### 16. Proceed Flow, Short History, Reasoning & Error Summary Fixes
- **Proceed Flow Fix:**
  - `ChatViewModel.proceedPlan()` now appends the exact user message: `"The plan is approved. Start building now, beginning with milestone M1."` (saved in `chat.json` and shown in the UI), ensuring the request sent to the model always ends with a user message.
- **Short History & System Prompt in Build Mode:**
  - When starting a stream in build mode (`ChatMode.BUILD` or `ModeState.isBuildPhase`), `ChatViewModel` sends the build system prompt as the system message and restricts conversation history to the last few messages (`chat.messages.takeLast(4)`), ensuring repetition is avoided while keeping the approved plan in the system prompt.
- **Reasoning & Tool Calls Handling:**
  - Added `LlmEvent.Reasoning` and updated `OpenAiSseParser` to parse `reasoning_content`, `reasoning`, and `tool_calls` deltas.
  - Treated streams with only reasoning or tool calls as non-empty (`hasReceivedData = true`), showing reasoning text as progress text (`progressMessage`) instead of throwing an "empty response from model" error.
- **Request Summary in Error Bubbles:**
  - `OpenAiCompatibleClient` and `ChatViewModel` now format and display a comprehensive request summary in error bubbles upon HTTP errors, exceptions, or empty responses: URL, model, roles of messages sent, system prompt character count, HTTP status, and the first 200 characters of the raw response (never the API key).

### 17. Reference Evaluation & Reuse Analysis (`reference/`)

1) **In `reference/agent-impl`, how is `AgentLoop` constructed? List the constructor arguments, including the `AgentWorkspace` and the provider/config:**
   - `client: LlmClient`
   - `model: String`
   - `tools: AgentToolRegistry` (populated via `builtinTools(ws)`, where `ws` is `AgentWorkspace`)
   - `gate: AgentPermissionGate`
   - `systemPrompt: () -> String`
   - `sessionContext: () -> String? = { null }`
   - `maxTokens: Int = 8192`
   - `maxIterations: Int = 24`
   - `thinkingBudget: Int? = null`
   - `webSearch: Boolean = false`
   - `effort: String? = null`
   - `compactor: HistoryCompactor = HistoryCompactor()`
   - `pacer: RequestPacer? = null`
   - `checkpoints: CheckpointWorkspace? = null`
   - `clock: () -> Long = System::currentTimeMillis`
   *Note:* `AgentWorkspace` is passed to `builtinTools(ws)` to create the tool registry, not directly to `AgentLoop`'s constructor. Provider configuration is supplied via `ProviderConfig` to `LlmProvider.client(config)` which returns the `LlmClient`.

2) **In `reference/agent-ui`, where does `AgentUiPlugin` get the real `AgentWorkspace` from (which service or API of the host)? Can an installed plugin like mine get that same object through the plugin API? Quote the class and function names:**
   - In `AgentUiPlugin` (`dev.ide.agent.ui.AgentUiPlugin`), `contributeUi(scope: UiContributionScope)` registers tool windows and overlays:
     ```kotlin
     override fun contributeUi(scope: UiContributionScope) {
         scope.toolWindow(
             ToolWindowContribution(
                 id = "agent.chat",
                 title = "AI",
                 iconId = "sparkle",
                 anchor = ToolWindowAnchor.RIGHT,
                 content = { ctx -> ChatDrawer(ctx.backend, fileActions = ctx.fileActions, activeFilePath = ctx.activeFilePath) },
             ),
         )
         scope.overlay(
             OverlayContribution(
                 id = "agent.permission",
                 content = { ctx -> AgentPermissionDialog(ctx.backend) },
             ),
         )
     }
     ```
   - `AgentUiPlugin` gets host services via `ctx.backend` (`IdeBackend`). Installed plugins interact with the host through `UiContributionScope`, `ToolWindowContribution`, and `IdeBackend`.

3) **Can `OpenAiProvider` be configured with my own `baseUrl`, `apiKey` and `model` from `ProviderConfigStore`?**
   - Yes. `OpenAiProvider.client(config: ProviderConfig)` accepts a `ProviderConfig` object containing `baseUrl`, `apiKey`, and `modelName`, using them to configure the HTTP request transport and endpoint URLs.

4) **If it all works, say "REUSE POSSIBLE" and list the exact steps. If not, say "REUSE NOT POSSIBLE" and why:**
   - **REUSE NOT POSSIBLE**. While reference modules exist under `reference/`, our plugin implements its own custom lightweight protocol parser (`ProtocolParser`), state machine (`ModeController`), chat repository (`ChatRepository`), and UI components (`AiStudioScreen`, `PlanCard`, `QuestionCard`) specifically tailored to CodeAssist's in-process ART plugin environment and `codeassist_plugin.toml` manifest without depending on external reference agent packages.

### 19. Crash-Proof Plugin, Streaming Performance, Persistence & Memory Capping Fixes
- **Chat Loading & Corrupt Handling:** Wrapped `ChatRepository.get` and `list` in try/catch. Corrupt/unreadable chat folders are automatically moved to `chats_v2/_corrupt/<id>`, logged, and skipped. On startup, `ChatViewModel.loadInitialChat()` opens a fresh empty chat instead of crashing if loading throws.
- **Streaming Performance & 80ms Batching:** Tokens are collected in a buffer and published via `streamingText` at most every ~80 ms. While streaming, `StreamingMessageBubble` renders `streamingText` as plain text (no Markdown parsing), parsing Markdown and protocol only when the message is complete. Blinking cursor updates do not re-trigger message list recomposition.
- **Exceptions & Robustness:** Added `CoroutineExceptionHandler` in `ChatViewModel` and try/catch around stream collection, `ProtocolParser`, and rendering helpers so errors result in a visible error bubble and never crash the app.
- **Persistence & Interruption:** Chat data is saved to disk only when a message is complete or the stream ends (including Stop and error), with atomic writes. Interrupted streams save a partial reply.
- **Memory & Character Capping:** Single messages are strictly capped at 100,000 characters in UI state and in `chat.json`.
- **ProtocolParser Performance:** Optimized `ProtocolParser.parseBuffer` for long text without tags using `$O(N)` `indexOf('<')` check, preventing quadratic behavior on 50k+ character texts. Added unit test verifying fast performance on 50,000 characters fed in 20-character chunks.

### 20. Native Tool Calling in Build Mode
- **Native Tool Calling (`LlmClient`, `OpenAiCompatibleClient`, `OpenAiSseParser`):**
  - Added optional `tools` list in OpenAI function format to `LlmClient.stream()`.
  - `OpenAiSseParser` accumulates streamed `delta.tool_calls` by index (`id`, `name`, argument fragments), exposes `finishReason`, and emits `LlmEvent.ToolCall` when the stream ends.
- **Messages & Storage (`ChatMessage`, `chat.json`):**
  - Supported assistant messages carrying `tool_calls` and `"tool"` role messages carrying `tool_call_id`. Saved in `chat.json`.
- **Project Tools (`ProjectTools`):**
  - Implemented `list_directory`, `read_file`, `write_file` (with atomic write via temp file), `create_directory`, and `delete_file`.
  - Path safety rules: rejects absolute paths and `".."` traversal outside project root.
- **Build Loop & Continuation:**
  - Build loop runs up to 40 rounds, executing independent tool calls sequentially, appending assistant `tool_calls` messages and tool result messages, compacting older `write_file` content in request history, handling milestone continuation (`"Continue with the next milestone."` up to 10 times), and transitioning to `VERIFYING` once all milestones are done.
- **UI & Permissions:**
  - One-time permission card per chat (`"Allow AI Studio Mode to create and edit files in this project?"` Allow / Deny).
  - Collapsed row items for tool calls (`"✓ write_file app/src/Foo.kt"`).
- **Unit Testing:**
  - Unit tests for tool_calls stream parsing (fragmented arguments), path safety rules, and `FakeLlmClient` tool call testing.

### 21. Project Dependencies Declaration & Real File Access
- **Dependency Declaration:** CodeAssist projects declare dependencies in `module.toml` under the `[dependencies]` section (`implementation = [...]`, `compileOnly = [...]`) using standard Maven coordinates (group:artifact:version) without Gradle configuration files.
- **Project File Access:** `ProjectToolExecutor` (`ProjectTools`) provides real project file operations (`list_directory`, `read_file`, `write_file`, `create_directory`, `delete_file`) relative to the project root, enforcing path safety (rejecting absolute paths and `..`), atomic file writes, 20,000 character read limits, and blocking writes to Gradle files (`build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`).
- **File Permission Card:** One-time per chat permission prompt ("Allow AI Studio Mode to create and edit files in this project?") gating mutating tool operations.

### 22. Build Loop Lifecycle, Loop Guard & Round Limit
- **Lifecycle Independence:** Build loops run in `BuildLoopManager.scope` (`SupervisorJob + Dispatchers.Default`) independent of tool window or ViewModel lifecycles, and are cancelled only on Stop, error, or plugin disable. `chat.json` is saved after every round. Reopening the tool window reloads the current state and resumes observing/following the live loop.
- **Loop Guard:** Detects identical tool calls 3 times in a row or >3 writes to the same path in a milestone, replying with `"You already did this. Move on to the next step."`. On the 2nd warning, pauses the build with a message and Continue / Stop buttons.

### 23. Project Root Validation & Settings Key Alignment
- **Project Root Settings Key:** Aligned settings key with `ProviderConfigStore.KEY_PROJECT_ROOT` ("projectRoot").
- **Validation & Probe:** Validation checks exists, isDirectory, canRead, canWrite (using `.aistudio_probe` temp file creation and deletion), file list count, and `module.toml` presence at root or direct subfolder.
- **Path Trimming & Logging:** Saved paths are trimmed of spaces, newlines, quotes, and trailing slashes. Logs package name, process UID, application class name, and external files dir, and displays detailed check info in the error/pause card.

### 24. Build Stamp & System Prompt Versioning
- **Build Stamp:** Added `const val BUILD_STAMP = "2026-10-09-B"` in `ProviderConfigStore`.
- **Settings Page & Dialog:** Displayed `"Build: 2026-10-09-B"` at the bottom of both `AiStudioSettingsPage` and `AiStudioSettingsDialog`.
- **System Prompt Prefix:** Automatically prepended `"PLUGIN_BUILD: 2026-10-09-B\n"` as the very first line of every system prompt generated by `ModeController.systemPromptFor()`.

- **Round Limit:** Raised to 60 rounds, with a `"Paused after 60 steps, Continue?"` card instead of failing.

### 23. Build Tools & Project Root Alignment Fixes
- **Project Root Discovery & Permissions:** `ProjectTools.findProjectRoot()` dynamically finds the open project root containing `module.toml` by walking parent directories. Permission card displays `"Project: <folder name>"`.
- **`list_directory` & Tool Results:** `list_directory` lists entries one per line, directories with trailing `/`, sorted, logging root path and entry count. Tool results (`write_file`, `create_directory`, `read_file`, `delete_file`) return informative success/not found messages.
- **Path Guard:** Rejects invalid paths where first segment does not exist and is not `"src"` (preventing creation of unauthorized folders like `app/`), and blocks writes/deletions inside hidden folders (starting with `"."`).
- **Prompt Templates (`{{CONVENTIONS}}` & `{{TREE}}`):** `{{CONVENTIONS}}` appends `module.toml` (max 4,000 chars) under `"CURRENT module.toml:"`. `{{TREE}}` uses depth 4, max 300 entries, skipping hidden folders and `build/`. First build message displays tree's first 15 lines and entry count in a small gray debug line.
- **Loop Guard Enhancements:** Counts "create_directory/write_file that changed nothing" as a repeated call and tells the model `"That already exists. Read it, or move to the next file."`.

### Step A Read-Only Report
1) **Places constructing `ProjectToolExecutor` or calling `ProjectTools` (and default roots):**
   - `ProjectToolExecutor.kt`, line 6: `class ProjectToolExecutor(private val rootDir: File = File("."))` — default root passed is `File(".")`.
   - `ChatViewModel.kt`, line 784: `val toolExecutor = com.copilotenhanced.app.tools.ProjectToolExecutor()` — default constructor uses `File(".")`.
   - `ProjectTools.kt`, line 10: `fun findProjectRoot(startDir: File = File("."), context: android.content.Context? = null)` — default `startDir` is `File(".")`. Line 37 uses `File(".").absoluteFile` as fallback.
   - `AiStudioScreen.kt`, line 177: `ProjectTools.findProjectRoot(context = context)` (defaults to `File(".")`).
   - `ChatMessageBubble.kt`, line 169: `ProjectTools.findProjectRoot()` (defaults to `File(".")`).
   - `ModeController.kt`, lines 121 & 157: `ProjectTools.findProjectRoot()` (defaults to `File(".")`).
   - `ChatRepository.kt`, line 253: `File(".")` used as parent fallback.
2) **Substitution of `{{TREE}}` and `{{CONVENTIONS}}`:**
   - Substituted in `ModeController.kt` (lines 153 & 154) when processing `prompts/build_mode.md`.
   - `{{TREE}}`: Filled with `fileTreeText` generated by `getProjectFileTree(rootDir, maxDepth = 5, maxEntries = 300)`.
   - `{{CONVENTIONS}}`: Filled with `PROJECT_CONVENTIONS.md` resource plus `"CURRENT <relative path>:"` and text of every `module.toml` found (max 4,000 characters each).
3) **Existence of T7 changes:**
   - `resolveProjectRoot`: Does not exist as a dedicated function yet.
   - Probe file (`.aistudio_probe`): Exists in `ChatViewModel.kt`.
   - `[root=...]` prefix on tool errors: Does not exist yet in tool execution returns.

### 24. Recent Fixes & Updates
- **Project Root Fallback Removal:** Removed all `File(".")` fallbacks from `ChatViewModel.kt` and `ChatMessageBubble.kt`. When project root cannot be resolved or is invalid, builds are prevented and show the exact error card message `"Project folder not found or not set. Set it in AI Studio Mode settings."` with the reason. `AiStudioScreen` permission card displays the same error instead of a path when invalid.
- **Path Validation & Root Meaning (`ProjectTools.kt`):** Removed top-level folder restriction for first path segment while maintaining relative path rules, no `..`, no hidden folder writes (`.` with length > 1), and no Gradle file writes. `""`, `"."`, `"./"`, and `"/"` all mean the project root (`list_directory` lists it, `create_directory` returns `"Already exists, nothing to do"`).

### 25. Tree Depth & Loop Guard Updates
- **Tree Depth (`{{TREE}}`):** Raised `maxDepth` to 8 (with 300 max entries) in `ModeController.getProjectFileTree()` so files under `app/src/main/kotlin/com/example/<package>/` appear, including `MainActivity.kt`.
- **Loop Guard Refinements:**
  - Compares real content / hash, never shortened arguments.
  - Writing an existing file with identical content returns `"No change: file already has this exact content."` (removing `"That already exists. Read it, or move to the next file."`).
  - Counts a call as repeated only if it returned an error or `"No change"` three times in a row on the same path.

### 27. Project Diagnostics Check
- Inspected project diagnostics and confirmed 0 compile errors.

### 28. Fix-Mode Loop & Tool Enhancements (Build Stamp 2026-10-09-E)
- **Pair-Safe Trimming:** History compaction / trimming drops assistant tool_calls messages together with all its tool results, and never keeps tool messages whose assistant messages were dropped.
- **`read_file` Enhancements:** Supports optional `start_line` and `end_line`, increased character limit to 30,000, appends truncation message when longer, and re-read guard (`"You already read this file twice and it has not changed. Act on it now."`).
- **`search_files(query)`:** Added case-insensitive text search tool over project text files (skipping hidden folders and build/), returning at most 40 lines as `path:line: text`.
- **Compiler Error Digest:** Parses compiler error output into a concise digest (total error count, files with error counts, collapsed unresolved references per file, and unique other messages up to 40), keeping raw text out of the LLM prompt (max 6,000 characters).
- **Build Stamp:** Updated `BUILD_STAMP` to `"2026-10-09-E"`.

