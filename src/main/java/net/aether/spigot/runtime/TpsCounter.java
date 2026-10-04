package net.aether.spigot.runtime;

public final class TpsCounter implements Runnable {

    private final long[] samples = new long[20 * 60];
    private int index;
    private int filled;
    private long last = System.nanoTime();

    public void run() {
        long now = System.nanoTime();
        samples[index] = now - last;
        last = now;
        index = (index + 1) % samples.length;
        if (filled < samples.length) {
            filled++;
        }
    }

    public double tps(int seconds) {
        int wanted = Math.max(1, seconds * 20);
        int count = Math.min(filled, wanted);
        if (count <= 0) {
            return 20.0D;
        }
        long sum = 0L;
        for (int i = 0; i < count; i++) {
            int slot = index - 1 - i;
            if (slot < 0) {
                slot += samples.length;
            }
            sum += samples[slot];
        }
        double average = sum / (double) count;
        if (average <= 0.0D) {
            return 20.0D;
        }
        return Math.min(20.0D, 1_000_000_000.0D / average);
    }

    public String format() {
        return trim(tps(1)) + ", " + trim(tps(5)) + ", " + trim(tps(15));
    }

    private static String trim(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
