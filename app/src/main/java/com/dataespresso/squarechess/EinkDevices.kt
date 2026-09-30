package com.dataespresso.squarechess

import android.os.Build
import java.util.Locale

/**
 * Android has no API that says a screen is e-paper, so the first-run default of the e-ink
 * setting comes from the device's build identity. A wrong match turns e-ink mode on for a
 * colour screen, so a brand counts only when it makes nothing but e-ink devices; brands that
 * also make colour phones or tablets need an exact model. The player can always change it.
 *
 * Identities are from the device lists that e-reader apps (KOReader, Readest) keep.
 */
fun isKnownEinkDevice(): Boolean =
    isEinkIdentity(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT)

/** Brands that make only e-ink devices, matched against the manufacturer or the brand. */
private val EINK_BRANDS = setOf(
    "onyx", "boox", "boyue", "boeye", "likebook", "haoqing", "meebook", "bigme", "hanvon",
    "crema", "hyread", "fidibo", "storytel", "topjoy", "linfiny", "inkbook", "ridi", "dasung",
    "mobiscribe", "bookeen", "pocketbook", "supernote", "mudita"
)

/** Exact models from brands that also make colour screens: Hisense A5, A9 and Touch Lite. */
private val HISENSE_EINK_MODELS = setOf("hlte202n", "hlte556n", "hitv205n")

fun isEinkIdentity(manufacturer: String, brand: String, model: String, device: String, product: String): Boolean {
    fun clean(value: String) = value.trim().lowercase(Locale.ROOT)
    val maker=clean(manufacturer); val label=clean(brand); val name=clean(model)
    val codes=listOf(clean(device),clean(product))
    return when {
        maker in EINK_BRANDS || label in EINK_BRANDS -> true
        // Rockchip, Allwinner and NXP e-reader boards: px30_eink, rk3566_eink, evk_6sl_eink.
        codes.any { "eink" in it } -> true
        (maker=="hisense" || label=="hisense") && name in HISENSE_EINK_MODELS -> true
        // Nook GlowLight readers; Nook tablets with colour screens are BNTV models.
        maker=="barnesandnoble" && name.startsWith("bnrv") -> true
        label=="tolino" || "tolino" in name -> true
        name.startsWith("inkpalm") || name.startsWith("moaan") || name.startsWith("mooink") || name=="xiaomi_reader" -> true
        maker=="sony" && name.startsWith("dpt-") -> true
        label=="lenovo" && name=="lenovo sp101fu" -> true
        (label=="energysistem" || label=="energy_sistem") && name.startsWith("ereader") -> true
        else -> false
    }
}
