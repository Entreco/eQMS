plugins {
    id("eqms.web-app")
}

dependencies {
    add("wasmJsMainImplementation", project(":core:ledger"))
    add("wasmJsMainImplementation", project(":core:search"))
}
