# StreetMeasure

A tool to measure (street) widths via Google's ARCore.

## History

This used to be a component within [StreetComplete](https://github.com/streetcomplete/StreetComplete/) but has been outsourced into an own app due to license issues:
It turned out that Google's [ARCore library is not licensed under the Apache license after all](https://github.com/google-ar/arcore-android-sdk/issues/1538), but is closed source and its usage is subject to additional terms.

This means that this library cannot be used by any GPL licensed project, hence, I took the AR measuring stuff and released it in an own app under a permissive license which does not have this constraint.

## Usage

You can call this activity for result to let the user make a measurement and return its result to 
your app. See the [Android documentation](https://developer.android.com/training/basics/intents/result) on how to do this.

A type-safe `ActivityResultContract` interface is available at [MeasureContract.kt](https://github.com/streetcomplete/StreetMeasure/blob/master/app/src/main/java/de/westnordost/streetmeasure/MeasureContract.kt), you can just copy it.

Alternatively, consult the [documentation in the code](https://github.com/streetcomplete/StreetMeasure/blob/master/app/src/main/java/de/westnordost/streetmeasure/MeasureActivity.kt#L553-L607) which raw parameters are available and what is returned in the result
`Intent` if you want to do it the old way.

## License

This software is released under the terms of the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
