# PicPose Android Build Notes

## Native Debug Symbols For Play Console (AAB)

This app bundle contains native `.so` files from dependencies (for example `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`), so Play Console expects native debug symbols for better crash and ANR symbolication.

Release builds are configured with:

```kotlin
android {
  buildTypes {
    release {
      ndk {
        debugSymbolLevel = "FULL"
      }
    }
  }
}
```

`FULL` gives the most complete native stack decoding. If artifact size becomes a concern, switch to `SYMBOL_TABLE` for smaller but still useful symbols.

`bundleRelease` is also wired to package symbols into:
- `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`
- via Gradle task: `:app:packageReleaseNativeDebugSymbols`

### Build And Locate Symbols

1. Build release bundle:
   - `./gradlew :app:bundleRelease`
2. Find generated symbols at:
   - `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip` (upload this to Play)
   - or, if needed for inspection, under:
     - `app/build/intermediates/merged_native_libs/release/mergeReleaseNativeLibs/out/lib/<abi>/`
     - and stripped outputs under `app/build/intermediates/stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/<abi>/`

### Upload In Play Console

1. Open Play Console for the app.
2. Go to the release where the AAB is uploaded.
3. In the native symbols area, upload `native-debug-symbols.zip` as **Native debug symbols**.

Note: This Play Console message is a warning (not a blocking error), but uploading symbols is strongly recommended for useful native crash/ANR analysis.
