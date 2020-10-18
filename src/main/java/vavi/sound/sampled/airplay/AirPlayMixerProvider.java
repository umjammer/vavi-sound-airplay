/*
 * Copyright (c) 2020 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.sound.sampled.airplay;

import java.util.logging.Level;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.Mixer.Info;
import javax.sound.sampled.spi.MixerProvider;

import vavi.util.Debug;


/**
 * AirPlayMixerProvider.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (umjammer)
 * @version 0.00 2020/10/18 umjammer initial version <br>
 */
public class AirPlayMixerProvider extends MixerProvider {

    @Override
    public Info[] getMixerInfo() {
        return new Info[] { AirPlayTargetDataLine.mixerInfo };
    }

    @Override
    public Mixer getMixer(Info info) {
        if (info == AirPlayTargetDataLine.mixerInfo) {
Debug.println(Level.FINE, "★1 info: " + info);
            AirPlayTargetDataLine mixer = new AirPlayTargetDataLine();
            return mixer;
        } else {
Debug.println(Level.FINE, "not suitable for this provider: " + info);
            throw new IllegalArgumentException("info is not suitable for this provider");
        }
    }
}

/* */
