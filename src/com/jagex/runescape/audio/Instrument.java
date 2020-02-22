package com.jagex.runescape.audio;

import com.jagex.runescape.io.Buffer;

import java.util.Random;

public class Instrument {
    public static int[] sine;
    public static int[] output = new int[220500];
    public static int[] noise = new int[32768];
    public static int[] vol_step = new int[5];
    public static int[] pitch_step = new int[5];
    public static int[] delays = new int[5];
    public static int[] pitch_base_step = new int[5];
    public static int[] phases = new int[5];

    static {
        Random random = new Random(0L);
        for(int i = 0; i < 32768; i++)
            noise[i] = (random.nextInt() & 0x2) - 1;
        sine = new int[32768];
        for(int i = 0; i < 32768; i++)
            sine[i] = (int) (Math.sin((double) i / 5215.1903) * 16384.0);
    }

    public Filter filter;
    public int begin;
    public int delay_time;
    public int[] oscill_pitch_delta = new int[5];
    public Envelope gating_attack_env;
    public Envelope vol_mod_amp_env;
    public int[] oscill_delay = new int[5];
    public Envelope pitch_mod_env;
    public int[] oscill_vol;
    public int delay_feedback;
    public Envelope filter_env;
    public Envelope pitch_env;
    public Envelope vol_mod_env;
    public Envelope vol_env;
    public int duration;
    public Envelope gating_release_env;
    public Envelope pitch_mod_amp_env;

    public Instrument() {
        delay_time = 0;
        oscill_vol = new int[5];
        begin = 0;
        delay_feedback = 100;
        duration = 500;
    }

    public static void initialize() {
        output = null;
        noise = null;
        sine = null;
        phases = null;
        delays = null;
        vol_step = null;
        pitch_step = null;
        pitch_base_step = null;
    }

    public void decode(Buffer buffer) {
        pitch_env = new Envelope();
        pitch_env.decode(buffer);
        vol_env = new Envelope();
        vol_env.decode(buffer);
        int i = buffer.getUnsignedByte();
        if(i != 0) {
            buffer.currentPosition--;
            pitch_mod_env = new Envelope();
            pitch_mod_env.decode(buffer);
            pitch_mod_amp_env = new Envelope();
            pitch_mod_amp_env.decode(buffer);
        }
        i = buffer.getUnsignedByte();
        if(i != 0) {
            buffer.currentPosition--;
            vol_mod_env = new Envelope();
            vol_mod_env.decode(buffer);
            vol_mod_amp_env = new Envelope();
            vol_mod_amp_env.decode(buffer);
        }
        i = buffer.getUnsignedByte();
        if(i != 0) {
            buffer.currentPosition--;
            gating_release_env = new Envelope();
            gating_release_env.decode(buffer);
            gating_attack_env = new Envelope();
            gating_attack_env.decode(buffer);
        }
        for(int i_0_ = 0; i_0_ < 10; i_0_++) {
            int i_1_ = buffer.method502((byte) 104);
            if(i_1_ == 0)
                break;
            oscill_vol[i_0_] = i_1_;
            oscill_pitch_delta[i_0_] = buffer.getSignedSmart();
            oscill_delay[i_0_] = buffer.method502((byte) -77);
        }
        delay_time = buffer.method502((byte) 118);
        delay_feedback = buffer.method502((byte) -104);
        duration = buffer.getUnsignedShortBE();
        begin = buffer.getUnsignedShortBE();
        filter = new Filter();
        filter_env = new Envelope();
        filter.decode(buffer, filter_env);
    }

    public int evaluateWave(int phase, int amplitude, int table) {
        if(table == 1) {
            if((phase & 0x7fff) < 16384)
                return amplitude;
            return -amplitude;
        }
        if(table == 2)
            return sine[phase & 0x7fff] * amplitude >> 14;
        if(table == 3)
            return ((phase & 0x7fff) * amplitude >> 14) - amplitude;
        if(table == 4)
            return noise[phase / 2607 & 0x7fff] * amplitude;
        return 0;
    }

    public int[] synthesize(int n_s, int dt) {
        for(int i = 0; i < n_s; i++)
            output[i] = 0;
        if(dt < 10)
            return output;
        double f_s = (double) n_s / ((double) dt + 0.0);
        pitch_env.reset();
        vol_env.reset();
        int pitch_mod_step = 0;
        int pitch_mod_base_step = 0;
        int pitch_mod_phase = 0;
        if(pitch_mod_env != null) {
            pitch_mod_env.reset();
            pitch_mod_amp_env.reset();
            pitch_mod_step = (int) ((double) (pitch_mod_env.end - pitch_mod_env.start) * 32.768 / f_s);
            pitch_mod_base_step = (int) ((double) pitch_mod_env.start * 32.768 / f_s);
        }
        int vol_mod_step = 0;
        int vol_mod_base_step = 0;
        int vol_mod_phase = 0;
        if(vol_mod_env != null) {
            vol_mod_env.reset();
            vol_mod_amp_env.reset();
            vol_mod_step = (int) ((double) (vol_mod_env.end - vol_mod_env.start) * 32.768 / f_s);
            vol_mod_base_step = (int) ((double) vol_mod_env.start * 32.768 / f_s);
        }
        for(int i_7_ = 0; i_7_ < 5; i_7_++) {
            if(oscill_vol[i_7_] != 0) {
                phases[i_7_] = 0;
                delays[i_7_] = (int) ((double) oscill_delay[i_7_] * f_s);
                vol_step[i_7_] = (oscill_vol[i_7_] << 14) / 100;
                pitch_step[i_7_] = (int) ((double) (pitch_env.end - pitch_env.start) * 32.768 * Math.pow(1.0057929410678534, (double) oscill_pitch_delta[i_7_]) / f_s);
                pitch_base_step[i_7_] = (int) ((double) pitch_env.start * 32.768 / f_s);
            }
        }
        for(int i_8_ = 0; i_8_ < n_s; i_8_++) {
            int pitch_change = pitch_env.step(n_s);
            int vol_change = vol_env.step(n_s);
            if(pitch_mod_env != null) {
                int mod = pitch_mod_env.step(n_s);
                int mod_amp = pitch_mod_amp_env.step(n_s);
                pitch_change += evaluateWave(pitch_mod_phase, mod_amp, pitch_mod_env.form) >> 1;
                pitch_mod_phase += (mod * pitch_mod_step >> 16) + pitch_mod_base_step;
            }
            if(vol_mod_env != null) {
                int mod = vol_mod_env.step(n_s);
                int mod_amp = vol_mod_amp_env.step(n_s);
                vol_change = vol_change * ((evaluateWave(vol_mod_phase, mod_amp, vol_mod_env.form) >> 1) + 32768) >> 15;
                vol_mod_phase += (mod * vol_mod_step >> 16) + vol_mod_base_step;
            }
            for(int i_15_ = 0; i_15_ < 5; i_15_++) {
                if(oscill_vol[i_15_] != 0) {
                    int i_16_ = i_8_ + delays[i_15_];
                    if(i_16_ < n_s) {
                        output[i_16_] += evaluateWave(phases[i_15_], vol_change * vol_step[i_15_] >> 15, pitch_env.form);
                        phases[i_15_] += ((pitch_change * pitch_step[i_15_] >> 16) + pitch_base_step[i_15_]);
                    }
                }
            }
        }
        /* gating effect */
        if(gating_release_env != null) {
            gating_release_env.reset();
            gating_attack_env.reset();
            int counter = 0;
            boolean muted = true;
            for(int i_19_ = 0; i_19_ < n_s; i_19_++) {
                int on_step = gating_release_env.step(n_s);
                int off_step = gating_attack_env.step(n_s);
                int threshold;
                if(muted)
                    threshold = gating_release_env.start + (((gating_release_env.end - gating_release_env.start) * on_step) >> 8);
                else
                    threshold = gating_release_env.start + (((gating_release_env.end - gating_release_env.start) * off_step) >> 8);
                counter += 256;
                if(counter >= threshold) {
                    counter = 0;
                    muted = !muted;
                }
                if(muted)
                    output[i_19_] = 0;
            }
        }
        /* delay effect */
        if(delay_time > 0 && delay_feedback > 0) {
            int delay = (int) ((double) delay_time * f_s);
            for(int i_24_ = delay; i_24_ < n_s; i_24_++)
                output[i_24_] += output[i_24_ - delay] * delay_feedback / 100;
        }
        /* filter */
        if(filter.num_pairs[0] > 0 || filter.num_pairs[1] > 0) {
            filter_env.reset();
            int t = filter_env.step(n_s + 1);
            int M = filter.compute(0, (float) t / 65536.0F);
            int N = filter.compute(1, (float) t / 65536.0F);
            if(n_s >= M + N) {
                int n = 0;
                int delay = N;
                if(delay > n_s - M)
                    delay = n_s - M;
                for(/**/; n < delay; n++) {
                    int i_30_ = (int) (((long) output[n + M] * (long) Filter.inv_unity) >> 16);
                    for(int i_31_ = 0; i_31_ < M; i_31_++)
                        i_30_ += (int) (((long) (output[n + M - 1 - i_31_]) * (long) (Filter.coef[0][i_31_])) >> 16);
                    for(int i_32_ = 0; i_32_ < n; i_32_++)
                        i_30_ -= (int) (((long) output[n - 1 - i_32_] * (long) (Filter.coef[1][i_32_])) >> 16);
                    output[n] = i_30_;
                    t = filter_env.step(n_s + 1);
                }
                delay = 128;
                for(; ; ) {
                    if(delay > n_s - M)
                        delay = n_s - M;
                    for(/**/; n < delay; n++) {
                        int i_33_ = (int) (((long) output[n + M] * (long) Filter.inv_unity) >> 16);
                        for(int i_34_ = 0; i_34_ < M; i_34_++)
                            i_33_ += (int) (((long) (output[n + M - 1 - i_34_]) * (long) (Filter.coef[0][i_34_])) >> 16);
                        for(int i_35_ = 0; i_35_ < N; i_35_++)
                            i_33_ -= (int) (((long) (output[n - 1 - i_35_]) * (long) (Filter.coef[1][i_35_])) >> 16);
                        output[n] = i_33_;
                        t = filter_env.step(n_s + 1);
                    }
                    if(n >= n_s - M)
                        break;
                    M = filter.compute(0, (float) t / 65536.0F);
                    N = filter.compute(1, (float) t / 65536.0F);
                    delay += 128;
                }
                for(/**/; n < n_s; n++) {
                    int i_36_ = 0;
                    for(int i_37_ = n + M - n_s; i_37_ < M; i_37_++)
                        i_36_ += (int) (((long) (output[n + M - 1 - i_37_]) * (long) (Filter.coef[0][i_37_])) >> 16);
                    for(int i_38_ = 0; i_38_ < N; i_38_++)
                        i_36_ -= (int) (((long) output[n - 1 - i_38_] * (long) (Filter.coef[1][i_38_])) >> 16);
                    output[n] = i_36_;
                    t = filter_env.step(n_s + 1);
                }
            }
        }
        /* clamp */
        for(int i_39_ = 0; i_39_ < n_s; i_39_++) {
            if(output[i_39_] < -32768)
                output[i_39_] = -32768;
            if(output[i_39_] > 32767)
                output[i_39_] = 32767;
        }
        return output;
    }
}