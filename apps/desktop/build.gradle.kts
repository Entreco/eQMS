plugins {
    id("eqms.jvm-app")
}

dependencies {
    implementation(project(":core:ledger"))
    implementation(project(":core:search"))
}
