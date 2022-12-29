# StreetMeasure

A tool to measure (street) widths via Google's ARCore.

## History

This used to be a component within [StreetComplete](https://github.com/streetcomplete/StreetComplete/) but has been outsourced into an own app due to license issues:
It turned out that Google's [ARCore library is not licensed under the Apache license after all](https://github.com/google-ar/arcore-android-sdk/issues/1538), but is closed source and its usage is subject to additional terms.

This means that this library cannot be used by any GPL licensed project, hence, I took the AR measuring stuff and released it in an own app under a permissive license which does not have this constraint.

## License

This software is released under the terms of the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
