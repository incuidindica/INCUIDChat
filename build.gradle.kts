plugins { java }

group = "com.incuid"
version = "1.3.0-phase4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.252-stable")
    compileOnly("net.luckperms:api:5.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release.set(25) }
tasks.test { useJUnitPlatform() }
