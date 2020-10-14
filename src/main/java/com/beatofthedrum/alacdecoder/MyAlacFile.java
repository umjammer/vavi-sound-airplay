package com.beatofthedrum.alacdecoder;

public class MyAlacFile {

    AlacFile alacFile;

    public MyAlacFile(AlacFile newfile) {
        this.alacFile = newfile;
    }

    public void setMaxSamplesPerFrame(int value) {
        alacFile.setinfo_max_samples_per_frame = value;
    }

    public void set7a(int value) {
        alacFile.setinfo_7a = value;
    }

    public void setSamplesSize(int value) {
        alacFile.setinfo_sample_size = value;
    }

    public void setRiceHistoryMult(int value) {
        alacFile.setinfo_rice_historymult = value;
    }

    public void setRiceInitialHistory(int value) {
        alacFile.setinfo_rice_initialhistory = value;
    }

    public void setRiceKModifier(int value) {
        alacFile.setinfo_rice_kmodifier = value;
    }

    public void set7f(int value) {
        alacFile.setinfo_7f = value;
    }

    public void set80(int value) {
        alacFile.setinfo_80 = value;
    }

    public void set82(int value) {
        alacFile.setinfo_82 = value;
    }

    public void set86(int value) {
        alacFile.setinfo_86 = value;
    }

    public void set8aRate(int value) {
        alacFile.setinfo_8a_rate = value;
    }
}