plugins {
    id("xyz.jpenilla.run-paper") version "2.3.1"
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
}
