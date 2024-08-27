buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath(kotlin("gradle-plugin", version = "2.0.20"))
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}