plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

tasks.register("installGitHooks") {
    doLast {
        val target = file(".git/hooks/pre-push")
        val source = file("scripts/hooks/pre-push")
        target.delete()
        java.nio.file.Files.createSymbolicLink(target.toPath(), source.absoluteFile.toPath())
        println("Git hook installed: pre-push")
    }
}

tasks.configureEach {
    if (name == "preBuild") dependsOn("installGitHooks")
}
