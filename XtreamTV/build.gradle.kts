import java.util.Properties

dependencies {
    implementation("com.google.android.material:material:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.ktor:ktor-client-android:2.3.12") // Use a Ktor engine suitable for your platform
}

// use an integer for version numbers
version = 1


android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }


}


cloudstream {
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("blackcat91", "dogior")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://www.shutterstock.com/image-vector/iptv-vector-line-icon-ip-260nw-1841427610.jpg"
    description = "General IPTV player. Add a list in the settings and you'll see it in the plugins list in the home"
    requiresResources = true
}
