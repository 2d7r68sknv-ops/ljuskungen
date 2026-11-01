plugins { java }
group = "se.torchglow"
version = "0.1.0"
repositories { maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:26.3.build.218-stable") }
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.jar { archiveBaseName.set("TorchGlow") }
