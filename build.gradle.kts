buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.4.0")
        classpath(kotlin("gradle-plugin", version = "1.8.0"))
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}