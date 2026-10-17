# Gallery

A small, fast photo and video gallery for Android, built with Jetpack Compose and Material 3 Expressive.

- Photos timeline and albums, with sorting, grouping by day, month, type or folder, and filters
- Smooth viewer with zoom, video playback, details and slideshow
- Trash that keeps deleted items for 30 days
- Editor with crop, rotate, flip, filters, adjustments and drawing
- Hidden and excluded folders

## Building

```
./gradlew assembleRelease
```

Release builds are signed when a `keystore.properties` file with `storeFile`, `storePassword`,
`keyAlias` and `keyPassword` exists in the project root.

## License

GPL-3.0, see [LICENSE](LICENSE).
