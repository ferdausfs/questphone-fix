// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false


    // Aligned with the Hilt compiler version in the catalog (was hardcoded 2.51
    // while compiler was 2.56.2 — plugin/compiler skew breaks Hilt aggregation)
    alias(libs.plugins.hilt) apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false
    alias(libs.plugins.android.library) apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
}
