# R8 rules for the release build.
#
# Most rules come bundled with the libraries and are applied automatically:
# - kotlinx-serialization-core keeps the Companion and serializer() of every @Serializable class. The JSON field
#   and enum names live as string literals in the generated $$serializer classes, so renaming the model classes
#   does not change the backup format or how assets/catalog/foods.json is read.
# - room-runtime keeps every RoomDatabase subclass with its constructor.
# - lifecycle-viewmodel keeps the constructors the default ViewModel factory calls.
# The rules below pin the app's own runtime lookups so they do not depend on those library files alone.
# Do not replace them with a blanket -keep of az.healthchecker.**: that disables shrinking of the app code.

# Room builds the database by loading "<database class>_Impl" by name.
-keep class az.healthchecker.data.HealthDatabase
-keep class az.healthchecker.data.HealthDatabase_Impl { <init>(); }

# viewModel() in HealthApp creates AppViewModel through its (Application) constructor via reflection.
-keepclassmembers class az.healthchecker.ui.AppViewModel { <init>(android.app.Application); }

# Backup, Room payloads and the bundled catalog are JSON of the domain models: keep their serializer entry points.
# <1> is only the part matched by **, hence the az.healthchecker. prefix in the back-references.
-if @kotlinx.serialization.Serializable class az.healthchecker.**
-keepclassmembers class az.healthchecker.<1> { static az.healthchecker.<1>$Companion Companion; }
-keepclassmembers class az.healthchecker.**$Companion { kotlinx.serialization.KSerializer serializer(...); }

# Readable stack traces from logcat; retrace them with app/build/outputs/mapping/release/mapping.txt of the same build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
