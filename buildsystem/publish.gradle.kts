group = "org.videolan.android"

val libraryVersion = extra.get("library_version")?.toString() ?: ""
version = libraryVersion

val android = project.extensions.getByName("android")

val sourcesJar = tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    val sourceSets = android.javaClass.getMethod("getSourceSets").invoke(android) as org.gradle.api.NamedDomainObjectContainer<*>
    val mainSet = sourceSets.getByName("main")
    val javaObj = mainSet.javaClass.getMethod("getJava").invoke(mainSet)
    val srcDirs = javaObj.javaClass.getMethod("getSrcDirs").invoke(javaObj)
    from(srcDirs)
}

val javadoc = tasks.register<Javadoc>("javadoc") {
    val sourceSets = android.javaClass.getMethod("getSourceSets").invoke(android) as org.gradle.api.NamedDomainObjectContainer<*>
    val mainSet = sourceSets.getByName("main")
    val javaObj = mainSet.javaClass.getMethod("getJava").invoke(mainSet)
    val srcDirs = javaObj.javaClass.getMethod("getSrcDirs").invoke(javaObj)
    source(srcDirs)
    val bootClasspath = android.javaClass.getMethod("getBootClasspath").invoke(android) as List<*>
    classpath += files(bootClasspath.joinToString(File.pathSeparator))
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    dependsOn(javadoc)
    from(javadoc.get().destinationDir)
}

artifacts {
    add("archives", javadocJar)
    add("archives", sourcesJar)
}
