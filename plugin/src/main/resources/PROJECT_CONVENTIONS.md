# CodeAssist project conventions (this IDE does NOT use Gradle)

PROJECT ROOT AND LAYOUT
- All paths are relative to the project root. The root holds the module folder (normally "app/") and hidden IDE folders.
- Layout of the app module:
  app/module.toml
  app/proguard-rules.pro
  app/src/main/AndroidManifest.xml
  app/src/main/kotlin/<namespace as folders>/   (Kotlin sources)
  app/src/main/res/values|drawable|mipmap-*/    (resources)
  app/src/main/assets/ and app/src/main/resources/  (optional)
- A working Compose starter already exists: MainActivity.kt, the manifest, themes.xml, colors.xml, strings.xml, launcher icons. Read them and edit them. Never recreate the skeleton and never create folders that already exist.
- The package is the "namespace" under [android] in app/module.toml (its current text is shown below). Source files go in app/src/main/kotlin/ plus the namespace with dots as slashes.
- Hidden folders (.platform and others) are managed by the IDE: never touch them.
- Never create Gradle files (build.gradle*, settings.gradle*, libs.versions.toml).

DEPENDENCIES (app/module.toml)
- Syntax: a single array under [dependencies]:
  implementation = ["kotlin-stdlib", "group:artifact:version", ...]
- To add a library: read_file app/module.toml, add one "group:artifact:version" string to that implementation array, and write the whole file back with every other line unchanged. Never edit other sections (sourceSets, android, compiler plugins, signing).
- Already included: activity-compose, compose ui, foundation, material3, ui-tooling-preview. Compose is enabled.
- Not included by default: navigation-compose, lifecycle-viewmodel-compose, kotlinx-coroutines-android, material-icons-extended. Add only what the plan needs, with versions compatible with the existing Compose 1.7.x and material3 1.3.x.
- Avoid libraries that need annotation processing or compiler plugins (Room, Hilt/Dagger, KSP, kapt).
- Say in the progress note whenever you change module.toml.

CODE
- Single activity: edit the existing MainActivity.kt; put screens, theme and data code in separate files in the same package.
- minSdk is 21: do not use newer Android APIs without a Build.VERSION.SDK_INT check.
- Store data with SharedPreferences or plain files. org.json is built into Android and needs no dependency.
- Colors and typography in Kotlin (a ui/theme folder), user-visible strings in res/values/strings.xml.