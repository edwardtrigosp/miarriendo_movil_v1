import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

// Valores locales (local.properties no se sube a Git).
val propiedadesLocales = Properties().also { propiedades ->
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) {
        archivo.inputStream().use { propiedades.load(it) }
    }
}

// Clave de Google Maps (Maps SDK for Android): se lee de MAPS_API_KEY en local.properties.
// Si falta, la app compila y la pantalla del mapa avisa que hay que configurarla.
val mapsApiKey: String = propiedadesLocales.getProperty("MAPS_API_KEY", "")

// Servidor de pagos (carpeta backend/). En el emulador, 10.0.2.2 es el PC; con un teléfono real hay que
// poner la IP del PC en la red local o la URL pública del servidor, en BACKEND_URL de local.properties.
val backendUrl: String = (project.findProperty("backendUrl")
    ?: propiedadesLocales.getProperty("BACKEND_URL", "http://10.0.2.2:8080")).toString()

// Solo para pruebas: con -PemuladoresFirebase=true (o USE_FIREBASE_EMULATORS=true en local.properties) las
// compilaciones debug usan los emuladores de Firebase del PC en lugar del proyecto real.
// El host desde el dispositivo: 10.0.2.2 en el emulador de Android; 127.0.0.1 con "adb reverse" (teléfono por cable).
val hostEmuladores: String = (project.findProperty("emuladoresHost") ?: "10.0.2.2").toString()
val usarEmuladores: String = (project.findProperty("emuladoresFirebase")
    ?: propiedadesLocales.getProperty("USE_FIREBASE_EMULATORS", "false")).toString()

// Interruptor del inicio unificado: -PinicioUnificado=true o INICIO_UNIFICADO=true en local.properties.
val inicioUnificado: String = (project.findProperty("inicioUnificado")
    ?: propiedadesLocales.getProperty("INICIO_UNIFICADO", "false")).toString()

android {
    namespace = "com.example.miarriendo_v1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.miarriendo_v1"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("boolean", "USE_FIREBASE_EMULATORS", usarEmuladores)
        buildConfigField("String", "EMULATORS_HOST", "\"$hostEmuladores\"")
        // Inicio unificado por módulos (InicioActivity). Apagado hasta la fase 4.
        buildConfigField("boolean", "INICIO_UNIFICADO", inicioUnificado)

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.maps)
    implementation(libs.biometric)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}