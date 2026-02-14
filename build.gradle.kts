plugins {
    kotlin("jvm") version "2.2.21"
    `java-library`
}

group = "com.david"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}