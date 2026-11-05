# Depter release rules.
# The app uses no reflection-based serialization; framework classes (org.json, SQLite, PdfDocument) are not shrunk.

# Keep line numbers for readable crash reports in Play Console, hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Receivers and activities are referenced from the manifest and kept automatically by AAPT rules.
