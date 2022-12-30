# StreetMeasure

A tool to measure (street) widths via Google's ARCore.

## History

This used to be a component within [StreetComplete](https://github.com/streetcomplete/StreetComplete/) but has been outsourced into an own app due to license issues:
It turned out that Google's [ARCore library is not licensed under the Apache license after all](https://github.com/google-ar/arcore-android-sdk/issues/1538), but is closed source and its usage is subject to additional terms.

This means that this library cannot be used by any GPL licensed project, hence, I took the AR measuring stuff and released it in an own app under a permissive license which does not have this constraint.

## Usage

### Requesting a measure result

You can call this activity for result to let the user make a measurement and return its result to 
your app. See the [Android documentation](https://developer.android.com/training/basics/intents/result)
on how to do this generally.

A type-safe `ActivityResultContract` interface is available at [MeasureContract.kt](https://github.com/streetcomplete/StreetMeasure/blob/master/app/src/main/java/de/westnordost/streetmeasure/MeasureContract.kt), you can just copy it.

Alternatively, consult the [documentation in the code](https://github.com/streetcomplete/StreetMeasure/blob/master/app/src/main/java/de/westnordost/streetmeasure/MeasureActivity.kt#L567-L616) which raw parameters are available and what is returned in the result
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
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    && context.getSystemService<ActivityManager>()!!.deviceConfigurationInfo.glEsVersion.toDouble() >= 3.1
    && (
      context.packageManager.getLaunchIntentForPackage("de.westnordost.streetmeasure") != null
      || context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=de.westnordost.streetmeasure")), 0) != null
    )
```

- it is not available in Android versions below 7.0 and requires OpenGL ES 3.1
- if not already installed, the measuring app is only available on Google Play store (not on 
  F-Droid). So if neither of the two is installed, no point in showing it to the user as an option

If it returns true, it may still be the case that [his device is not supported](https://developers.google.com/ar/devices)
but this is something we cannot check for at this point.

Do not forget to add `<package android:name="de.westnordost.streetmeasure"/>` to the 
[`<queries>`](https://developer.android.com/guide/topics/manifest/queries-element) block in your 
Android manifest. In Android 11 onwards, it must be declared which packages the app should be
capable of communicating with.

## License

This software is released under the terms of the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
