# StreetMeasure

A tool to measure (street) widths via Google's ARCore.

## History

This used to be a component within [StreetComplete](https://github.com/streetcomplete/StreetComplete/) but has been outsourced into an own app due to license issues:
It turned out that Google's [ARCore library is not licensed under the Apache license after all](https://github.com/google-ar/arcore-android-sdk/issues/1538), but is closed source and its usage is subject to additional terms.

This means that this library cannot be used by any GPL licensed project, hence, I took the AR measuring stuff and released it in an own app under a permissive license which does not have this constraint.

For this reason, the app is also not available on F-Droid and never will be but only on Google Play. However, you can <a href="https://github.com/streetcomplete/StreetMeasure/releases/">download the APK here from Github</a> as well.

## Usage

### Requesting a measure result

You can call this activity for result to let the user make a measurement and return its result to 
your app. See the [Android documentation](https://developer.android.com/training/basics/intents/result)
on how to do this generally.

A type-safe `ActivityResultContract` interface is available at [MeasureContract.kt](https://github.com/streetcomplete/StreetMeasure/blob/master/app/src/main/java/de/westnordost/streetmeasure/MeasureContract.kt), you can just copy it.

Alternatively, consult the [documentation in the code](https://github.com/streetcomplete/StreetMeasure/blob/master/app/src/main/java/de/westnordost/streetmeasure/MeasureActivity.kt#L577-L633) which raw parameters are available and what is returned in the result
`Intent` if you want to do it the old way with [`Activity.startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)).

In either case, do not forget to cover the likely case that the measure app is not installed yet, 
i.e. catch the `ActivityNotFoundException` and for example forward the user to the Play Store:

```kotlin
startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=de.westnordost.streetmeasure")))
```

### Checking if AR measure is available at all

If the following function does return false, measuring with AR will not work anyway, so in that case
you probably don't even want to show the user the option to do so.

```kotlin
fun hasArMeasureSupport(context: Context): Boolean =
  // extra requirements for Sceneform: min Android SDK and OpenGL ES 3.1
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
  && context.getSystemService<ActivityManager>()!!.deviceConfigurationInfo.glEsVersion.toDouble() >= 3.1
  // Google Play is required to lead the user through installing the app
  && (
    // app is already installed
    context.packageManager.isPackageInstalled("de.westnordost.streetmeasure")
    // or at least google play is installed
    || context.packageManager.isPackageInstalled("com.android.vending")
  )

private fun PackageManager.isPackageInstalled(packageName: String): Boolean =
  try { getPackageInfo(packageName, 0) != null } catch (e: NameNotFoundException) { false }
```

- it is not available in Android versions below 7.0 and requires OpenGL ES 3.1
- if not already installed, the measuring app is only available on Google Play store (not on 
  F-Droid). So if neither of the two is installed, no point in showing it to the user as an option. It is of course possible to load the app from GitHub and also to sideload ARCore (="Google Play Services for AR") from some other more or less trustworthy source, but this is nothing the user could be led through automatically in the UI.

If it returns true, it may still be the case that [his device is not supported](https://developers.google.com/ar/devices)
but this is something you cannot check for at this point. The list of supported devices is not really publicly available directly from Google as a CSV and in any case is updated all the time, so getting the CSV one time from a third party and then not updating it is bound to break for new devices being added to the list.

In any case, do not forget to add `<package android:name="de.westnordost.streetmeasure"/>` and
`<package android:name="com.android.vending"/>` to the 
[`<queries>`](https://developer.android.com/guide/topics/manifest/queries-element) block in your 
Android manifest. In Android 11 onwards, it must be declared which packages the app should be
capable of communicating with, otherwise the app may not find the app at all.

## License

This software is released under the terms of the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
