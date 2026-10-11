plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation(project(":warbcraft-core"))
}

tasks.withType<JavaCompile>().configureEach {
    // Paper API'sindeki eski/uyarili yuzeyler yuzunden eklenti modulunde uyarilar hata sayilmaz.
    options.compilerArgs.remove("-Werror")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    // Cekirdek modulu eklenti jar'ina gomulur; sunucuya tek dosya kopyalanir.
    val core = project(":warbcraft-core").tasks.named<Jar>("jar")
    dependsOn(core)
    from(core.map { zipTree(it.archiveFile) })
    archiveBaseName.set("Warbcraft")
}

tasks.runServer {
    minecraftVersion("1.21.1")
    // Yerel deneme: sezon acik, yerlesimler spawn cevresinde (WarbcraftPlugin.TEST_MODE).
    jvmArgs("-Dwarbcraft.deneme=true", "-Dfile.encoding=UTF-8", "-Xms1G", "-Xmx2G")
    // Her surumden istemci baglanabilsin.
    downloadPlugins {
        url("https://github.com/ViaVersion/ViaVersion/releases/download/5.12.1/ViaVersion-5.12.1.jar")
        url("https://github.com/ViaVersion/ViaBackwards/releases/download/5.12.1/ViaBackwards-5.12.1.jar")
        url("https://github.com/ViaVersion/ViaRewind/releases/download/4.2.0/ViaRewind-4.2.0.jar")
    }
}
