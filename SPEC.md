# SPEC.md  (project contract: do not change without asking)

Goal: CodeAssist plugin "AI Studio Mode". Custom AI provider + Plan mode + Build mode.

Provider: OpenAI-compatible POST {baseUrl}/chat/completions, stream=true (SSE).
Settings: baseUrl, apiKey, modelName. Never log the apiKey.

Chat model: Chat{id,title,mode(PLAN|BUILD),messages[],plan?,planVersion,memory,createdAt,updatedAt}
Storage: reg.dataDir/chats/<chatId>/chat.json and memory.md. One memory per chat.

Mode state machine: IDLE, CLARIFYING, PLAN_READY, REVISING, APPROVED, BUILDING, VERIFYING, DONE, REJECTED.

Model output protocol (tagged blocks, parsed by the plugin; text outside tags is shown as chat):
  <questions>
  ## Question text?
  single | custom
  - 📝 Label | description
  </questions>
  <plan title="App Name" version="1">...markdown plan...</plan>
  <progress milestone="M1" status="started|done|failed">short note</progress>

Skills: markdown files with optional front-matter (name, description) stored in reg.dataDir/skills/.
Prompts: resources prompts/plan_mode.md and prompts/build_mode.md are loaded at runtime, never hardcoded.

UI: Compose, Material 3, one chat tool window + screens for history, settings.
Rules: reuse the default AI plugin's streaming/progress code where possible; no giant files (max ~300 lines);
every class has one job; unit-test parsers and the state machine.