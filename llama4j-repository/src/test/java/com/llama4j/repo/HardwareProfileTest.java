package com.llama4j.repo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HardwareProfileTest {

    @Test
    void testCreation() {
        HardwareProfile profile = new HardwareProfile(32.0, 8.0, true, true, false, 8);

        assertEquals(32.0, profile.totalRamGB(), 0.001);
        assertEquals(8.0, profile.gpuVramGB(), 0.001);
        assertTrue(profile.hasAvx2());
        assertTrue(profile.hasCuda());
        assertFalse(profile.hasMetal());
        assertEquals(8, profile.cpuCores());
    }

    @Test
    void testRecommendQuantization_smallModel_largeRam() {
        // 64GB total (64 RAM + 0 GPU) -> usable = 64 * 0.7 = 44.8GB
        // 7B model at Q8 = 7 * 1.10 = 7.7GB -> fits -> Q8_0
        HardwareProfile profile = new HardwareProfile(64.0, 0.0, true, false, false, 8);
        assertEquals("Q8_0", profile.recommendQuantization(7.0));
    }

    @Test
    void testRecommendQuantization_smallModel_mediumRam() {
        // 16GB total -> usable = 16 * 0.7 = 11.2GB
        // 7B at Q8 = 7.7GB -> fits -> Q8_0
        HardwareProfile profile = new HardwareProfile(16.0, 0.0, true, false, false, 8);
        assertEquals("Q8_0", profile.recommendQuantization(7.0));
    }

    @Test
    void testRecommendQuantization_largeModel_mediumRam() {
        // 16GB total -> usable = 11.2GB
        // 70B at Q2 = 70 * 0.35 = 24.5GB -> does NOT fit -> Q2_K (fallback)
        // 70B at Q4 = 70 * 0.60 = 42GB -> does NOT fit
        HardwareProfile profile = new HardwareProfile(16.0, 0.0, true, false, false, 8);
        assertEquals("Q2_K", profile.recommendQuantization(70.0));
    }

    @Test
    void testRecommendQuantization_mediumModel_withGpu() {
        // 16GB RAM + 24GB GPU = 40GB -> usable = 28GB
        // 13B at Q8 = 13 * 1.10 = 14.3GB -> fits in 28GB -> Q8_0
        HardwareProfile profile = new HardwareProfile(16.0, 24.0, true, true, false, 8);
        assertEquals("Q8_0", profile.recommendQuantization(13.0));
    }

    @Test
    void testRecommendQuantization_mediumModel_tightRam() {
        // 8GB RAM + 0GB GPU = 8GB -> usable = 5.6GB
        // 13B at Q2 = 13 * 0.35 = 4.55GB -> fits -> Q2_K
        // 13B at Q4 = 13 * 0.60 = 7.8GB -> does NOT fit
        HardwareProfile profile = new HardwareProfile(8.0, 0.0, true, false, false, 4);
        assertEquals("Q2_K", profile.recommendQuantization(13.0));
    }

    @Test
    void testRecommendQuantization_verySmallRam_smallModel() {
        // 4GB total -> usable = 2.8GB
        // 7B at Q2 = 7 * 0.35 = 2.45GB -> fits -> Q2_K
        // 7B at Q4 = 7 * 0.60 = 4.2GB -> does NOT fit
        HardwareProfile profile = new HardwareProfile(4.0, 0.0, false, false, false, 4);
        assertEquals("Q2_K", profile.recommendQuantization(7.0));
    }
}
