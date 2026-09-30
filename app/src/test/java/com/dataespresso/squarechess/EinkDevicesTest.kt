package com.dataespresso.squarechess

import org.junit.Assert.*
import org.junit.Test

class EinkDevicesTest {
    private fun eink(manufacturer: String, brand: String, model: String, device: String="", product: String="") =
        isEinkIdentity(manufacturer,brand,model,device,product)

    @Test fun einkOnlyBrandsMatch() {
        assertTrue(eink("ONYX","Onyx","NoteAir3C"))
        assertTrue(eink("Onyx","MaxLumi","MaxLumi"))
        assertTrue(eink("Boyue","Likebook","P78"))
        assertTrue(eink("haoqing","Meebook","M7"))
        assertTrue(eink("Bigme","Bigme","HiBreak Pro"))
        // Some readers report the chip maker; the brand still names the reader.
        assertTrue(eink("QUALCOMM","HANVON","Clear7","bengal_515"))
    }
    @Test fun einkBoardNamesMatch() {
        assertTrue(eink("rockchip","rockchip","Focus","px30_eink"))
        assertTrue(eink("Xiaomi","Xiaomi","xiaomi_reader","rk3566_eink"))
    }
    @Test fun mixedBrandsNeedAnEinkModel() {
        assertTrue(eink("Hisense","Hisense","HLTE556N"))
        assertTrue(eink("Hisense","Hisense","HLTE202N"))
        assertFalse(eink("Hisense","Hisense","Hisense E50"))
        assertTrue(eink("BarnesAndNoble","NOOK","BNRV700"))
        assertFalse(eink("BarnesAndNoble","NOOK","BNTV600"))
        assertTrue(eink("Sony","Sony","DPT-RP1"))
        assertFalse(eink("Sony","Sony","XQ-DQ54"))
        assertTrue(eink("rakutenkobo","rakutenkobo","tolino vision 6","tolino"))
    }
    @Test fun ordinaryDevicesDoNotMatch() {
        assertFalse(eink("Google","google","sdk_gphone64_arm64","emu64a","sdk_gphone64_arm64"))
        assertFalse(eink("Unihertz","Unihertz","Titan 2 Elite"))
        assertFalse(eink("Nothing","CMF","A001"))
        assertFalse(eink("Amazon","Amazon","KFTRWI"))
        assertFalse(eink("samsung","samsung","SM-S918B","dm3q","dm3qxxx"))
    }
}
