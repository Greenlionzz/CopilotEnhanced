# CopilotEnhanced

A CodeAssist plugin, packaged as its own app. CodeAssist finds it through the package manager, reads
`plugin/src/main/res/raw/codeassist_plugin.toml`, and loads `CopilotEnhancedUiPlugin` off the installed APK.

## Try it

Build and install this project, then restart CodeAssist and open **Settings > Plugins > Installed**.
Plugins load once at startup, so a newly installed plugin appears after the next launch. If it does
not appear, its row in that screen carries the reason.

## The three parts

| Part | Where |
| --- | --- |
| The packaged manifest | `plugin/src/main/res/raw/codeassist_plugin.toml` |
| The marker activity CodeAssist queries for | `plugin/src/main/AndroidManifest.xml` |
| The UI facet the manifest names | `CopilotEnhancedUiPlugin.kt` |

## Notes

- `id` is `CopilotEn`. It is the plugin's identity, not a display name: it decides load order, what
  another plugin's `dependsOn` refers to, and what an enable/disable choice is stored against.
- The SPI is a `compileOnly` dependency. It is never bundled: the IDE's classloader is the parent of
  this plugin's, so the SPI, the Kotlin stdlib and the Compose runtime resolve to the IDE's copies.
- Do not minify. The IDE loads the entry point by the name in the manifest, and R8 would rename it.
- The plugin runs inside the IDE's process, under its permissions. Class loading separates versions,
  not privileges.
- The SPI is licensed GPL-3.0-or-later with the Classpath exception, so this plugin can carry any
  license you choose.

## The UI facet

`CopilotEnhancedUiPlugin` implements `dev.ide.plugin.ui.UiPlugin`, so it contributes UI that renders itself: a
tool window here, and `screen` / `overlay` from the same registration object.

- Compose is `compileOnly` and pinned to the version CodeAssist bundles. Your `@Composable` code is
  compiled by the Compose compiler plugin (CodeAssist applies it automatically once the Compose
  runtime is on the classpath) and runs against the IDE's Compose at load time, so a newer Compose
  here than the IDE ships would fail at first composition rather than at build time.
- The panel gets a `UiContext`: where the user is, and how to open a file or another screen.
  Everything else belongs in an engine facet (`dev.ide.plugin.Plugin`, in `entryPoints`), which has
  the project model, the virtual file system, indexes and analysis. Both facets load off this one APK
  on one classloader, so they can share an `object` and call each other directly.
- Icons are ids in CodeAssist's own registry: a plugin has no `Context` for its own package, so it
  cannot ship drawables or use `stringResource`.

