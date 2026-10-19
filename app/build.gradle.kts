plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace="com.mypenc.swfview"
    compileSdk=34
    defaultConfig {
        applicationId="com.mypenc.swfview"
        minSdk=26
        targetSdk=34
        versionCode=1
        versionName="0.1"
        multiDexEnabled=true
    }
    buildTypes {
        release {
            isMinifyEnabled=false
            signingConfig=signingConfigs.getByName("debug") //sideload friendly
        }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled=true
    }
    kotlinOptions {
        jvmTarget="17"
    }
    packaging {
        resources {
            excludes+=setOf("META-INF/LICENSE*","META-INF/NOTICE*","META-INF/DEPENDENCIES","META-INF/INDEX.LIST","META-INF/*.kotlin_module")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs","include" to listOf("*.jar"))))
    implementation("javax.xml.stream:stax-api:1.0-2")
    implementation("com.fasterxml:aalto-xml:1.3.2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
