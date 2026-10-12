package com.jianzuo.writing;

/** Stable local theme IDs; old or unknown preferences keep the original appearance. */
final class WritingAppearance {
    static final String[] IDS = {"classic", "forest", "ocean", "rose", "mono"};
    record Palette(int paper, int sidebar, int ink, int muted, int accent, int line, int card) {}
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }
    static Palette palette(String id, boolean dark) {
        return switch (index(id)) {
            case 1 -> dark ? colors(0x19221D, 0x202C25, 0xE3EDE4, 0xADBCAF, 0x8DB99A, 0x35483C, 0x2B3D31)
                          : colors(0xF1F6EE, 0xE6EEE2, 0x29382D, 0x596756, 0x3F7450, 0xD4DFCE, 0xDCE9D6);
            case 2 -> dark ? colors(0x19222B, 0x202D38, 0xE3EBF2, 0xACBDCA, 0x8AB4D1, 0x364957, 0x2B3E4E)
                          : colors(0xF0F5FA, 0xE5EDF4, 0x293746, 0x566678, 0x3D6B91, 0xD2DDE8, 0xD8E5F0);
            case 3 -> dark ? colors(0x291E24, 0x34272E, 0xF0E4E9, 0xC3AFB9, 0xD7A0B7, 0x4B3942, 0x44303B)
                          : colors(0xFCF2F4, 0xF3E7EB, 0x443039, 0x755E6A, 0x97516F, 0xE6D5DD, 0xEFDFE6);
            case 4 -> dark ? colors(0x191919, 0x242424, 0xF0F0F0, 0xB5B5B5, 0xD0D0D0, 0x383838, 0x333333)
                          : colors(0xFFFFFF, 0xF3F3F3, 0x242424, 0x626262, 0x444444, 0xDFDFDF, 0xE9E9E9);
            default -> dark ? colors(0x1D1C1A, 0x252320, 0xEDE7DD, 0xB5ADA0, 0xD18561, 0x39352F, 0x332C25)
                           : colors(0xFAF7F1, 0xF3EFE8, 0x302E29, 0x81786C, 0xB05C3D, 0xE5DFD5, 0xEDE5D9);
        };
    }
    private static Palette colors(int paper, int sidebar, int ink, int muted, int accent, int line, int card) {
        return new Palette(opaque(paper), opaque(sidebar), opaque(ink), opaque(muted), opaque(accent), opaque(line), opaque(card));
    }
    private static int opaque(int rgb) { return 0xFF000000 | rgb; }
    /** Choose readable text for filled controls and each statistics heatmap cell. */
    static int foregroundOn(int background) {
        double luminance = channel((background >> 16) & 255) * .2126 + channel((background >> 8) & 255) * .7152 + channel(background & 255) * .0722;
        return luminance >= .179 ? 0xFF000000 : 0xFFFFFFFF;
    }
    private static double channel(int value) { double c = value / 255.0; return c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
    private WritingAppearance() {}
}
