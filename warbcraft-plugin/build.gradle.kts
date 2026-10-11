plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Skinli insan NPC'ler (askerler, haydutlar). 2.0.35, Minecraft 1.21.1'i destekleyen son surumdur.
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    compileOnly("net.citizensnpcs:citizensapi:2.0.35-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    // Web harita (istege bagli): yerlesimler, krallar ve lord ordulari isaretlenir.
    compileOnly("xyz.jpenilla:squaremap-api:1.3.2")
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
        // Harita on uretimi (/chunky): web haritada bos alan kalmasin.
        url("https://cdn.modrinth.com/data/fALzjamp/versions/P3y2MXnd/Chunky-Bukkit-1.4.40.jar")
        // Web harita: http://localhost:8080
        url("https://cdn.modrinth.com/data/PFb7ZqK6/versions/2WtLC9mv/squaremap-paper-mc1.21.1-1.3.2.jar")
        // Citizens 2.0.35 (derleme 3598): Minecraft 1.21.1'i destekleyen son surum.
        url("https://ci.citizensnpcs.co/job/Citizens2/3598/artifact/dist/target/Citizens-2.0.35-b3598.jar")
        url("https://github.com/ViaVersion/ViaVersion/releases/download/5.12.1/ViaVersion-5.12.1.jar")
        url("https://github.com/ViaVersion/ViaBackwards/releases/download/5.12.1/ViaBackwards-5.12.1.jar")
        url("https://github.com/ViaVersion/ViaRewind/releases/download/4.2.0/ViaRewind-4.2.0.jar")
    }
}
