# Graphic equalizer

SELFonium implements a ten-band **graphic equalizer (GEQ)** in its own playback DSP. It does not depend on the number of bands exposed by Android's system audio effect.

| Control | Behavior |
|---|---|
| Frequencies | 31, 63, 125, 250, 500 Hz; 1, 2, 4, 8, 16 kHz |
| Band gain / preamp | -12 to +12 dB in 0.25 dB steps; numeric entry is available |
| Presets | 18 built-in curves and saved custom curves with preamp |
| Automatic headroom | Estimates peak response and reduces gain to lower clipping risk |
| Graph | Calculated filter response, not a live music spectrum |

Preset curves are original settings, not headphone measurements or AutoEQ profiles. There is no PEQ editor, arbitrary center-frequency/Q control, AutoEQ import or system-wide processing.

## Processing

Ten cascaded peaking biquads use the [Audio EQ Cookbook](https://www.w3.org/TR/audio-eq-cookbook/) equations. Calculations and filter state use double precision with independent state for each channel. Configuration changes blend old/new output over about 10 ms; seek/flush resets filter history.

The DSP connects through Media3's [AudioProcessor](https://developer.android.com/reference/androidx/media3/common/audio/AudioProcessor). Output is PCM16; float output, audio offload and encoded passthrough are disabled. This is not bit-perfect or a high-resolution output mode. Once a configuration transition finishes, bypass and a flat zero-preamp curve preserve PCM16 samples. Bands near/above the available Nyquist range are skipped.

Automatic headroom estimates the frequency-response maximum and adds 0.5 dB margin. It can reduce volume and is not a dynamic limiter or compressor. Disabling it and applying excessive gain can still cause clipping.

Legacy system-EQ settings migrate once to the fixed ten-band curve using logarithmic interpolation and clamping. This does not modify sources, playlists or downloads.

## Validation

`bash scripts/test-core.sh` runs measured DSP checks for frequency response at 44.1/48 kHz, stereo isolation, preamp, headroom, bypass, reset and configuration migration. Mathematical tests do not assess perceived audio quality or replace device checks of Media3 and Bluetooth/USB routing.
