// Saf Java oyun mantigi. Sunucu API'sine bagimli degildir; tum kurallar burada test edilir.
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
