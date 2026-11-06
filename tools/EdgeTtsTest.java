package com.robot.arkadas;

/** CI check: Sec-MS-GEC tokens must match edge-tts 7.3.1 (drm.generate_sec_ms_gec_v2), then one live synthesis. */
public class EdgeTtsTest {
    public static void main(String[] a) throws Exception {
        String[][] v = {
            {"1700000000.0", "2CD23A2CA0B2595A-F3C846C370E05F9CC83ACC5F995C5F5D87A4D0F14CE8936A0F902E64099E38A2"},
            {"1762450000.5", "E9DA02C219643C2B-3785506CFAC7E65F30D12C89CC483A8E5B7E1329F57F7C42431A9BD360F937F8"},
            {"1800000123.0", "99E54519B490BE5C-E162DB0977FBDCB9B86DD8148BF1BCDC3C0303176CE39852A42B415935C20654"}};
        for (String[] t : v) {
            String got = SecMsGec.generate(Double.parseDouble(t[0]));
            if (!got.equals(t[1])) { System.out.println("TOKEN MISMATCH " + t[0] + " " + got); System.exit(1); }
        }
        System.out.println("Sec-MS-GEC tokens OK");
        for (String voice : new String[]{"tr-TR-EmelNeural", "tr-TR-AhmetNeural"}) {
            try {
                byte[] mp3 = EdgeTts.synth("Merhaba Gizem, ben Pamuk. Bugün nasılsın?", voice, "+0%", "+0Hz");
                System.out.println("Live Edge synthesis OK: " + voice + " " + mp3.length + " bytes");
            } catch (Exception e) {
                System.out.println("WARNING live Edge synthesis failed for " + voice + ": " + e); // network-dependent, not fatal
            }
        }
    }
}
