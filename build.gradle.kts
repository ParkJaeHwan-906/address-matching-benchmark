plugins {
    java
    application
}

group = "com.parkjaehwan"
version = "0.2.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "com.parkjaehwan.addressbench.Cli"
    applicationName = "address-bench"
    // 1천만 건 alias를 메모리에 올리는 LCS 기준선을 위해 heap을 넉넉히 잡는다.
    applicationDefaultJvmArgs = listOf("-Xms1g", "-Xmx6g", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
