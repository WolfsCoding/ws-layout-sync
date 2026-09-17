<p align="center">
  <img src="assets/logo.png" alt="Layout Sync logo" width="160">
</p>

# Layout Sync for WebStorm

Keeps the tool window layout identical across all open WebStorm project windows.

WebStorm stores the tool window layout (which bar each tool window sits on, its order, size,
docked/floating mode, split side, auto-hide) per project in `.idea/workspace.xml`. Settings Sync only
carries layouts you explicitly save under *Window | Layouts*. So rearranging tool windows in one
project window never affects the others. This plugin fixes that:

- Move, reorder, resize, dock, undock or hide a tool window bar in any window and every other open
  window follows immediately.
- The last layout is remembered at application level, so windows opened later start with it, even if
  their own `workspace.xml` says otherwise.
- Only the *structure* is synced. Whether a tool window is currently open or focused stays per window,
  so opening the terminal in one project does not pop it open everywhere.
- Toggle it via *Window | Layouts | Sync Layout Across Windows*. Re-enabling pushes the current window's
  layout to all others.

The stored layout lives in `options/layoutSync.xml` in the IDE config directory. Settings Sync picks
it up under its *Plugins* category, so a layout change on one machine is applied to open windows on
another machine when the sync arrives.

The main toolbar customization (*Customize Toolbar*) is already application-wide in WebStorm and
therefore not touched by this plugin.

## Build

Requires JDK 21. By default Gradle downloads the WebStorm version from `gradle.properties`; pass a
local install instead to skip the download:

```
./gradlew buildPlugin "-PlocalIdePath=C:/Program Files/JetBrains/WebStorm 2026.2.2"
```

The plugin zip ends up in `build/distributions/`. Install it via
*Settings | Plugins | ⚙ | Install Plugin from Disk*.

`./gradlew runIde` starts a sandboxed WebStorm with the plugin for manual testing.

## Release

Every push to `main` runs the *Release* workflow. It builds the plugin and, if no tag for the
`pluginVersion` in `gradle.properties` exists yet, tags `v<version>` and publishes a GitHub release
with the zip attached. Bump `pluginVersion` to cut a new release; pushes without a bump only build.

## Notes

- The plugin uses `DesktopLayout`, which JetBrains marks as internal API. It is the only way to read or
  apply a tool window layout, and the same class backs the built-in *Window | Layouts* feature.
- Compiled against WebStorm 2026.2. `since-build` is set to 2025.2, but older builds are untested.
