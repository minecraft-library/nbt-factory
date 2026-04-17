rootProject.name = "nbt-factory"

// Local Simplified-Dev checkout (optional); substitutes JitPack coordinates when present.
val simplifiedDev = file("../../Simplified-Dev")
if (simplifiedDev.isDirectory) includeBuild(simplifiedDev)
