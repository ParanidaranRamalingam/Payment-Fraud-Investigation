package com.capstone.fraudcase.knowledge;

import java.util.Locale;

/** Serialization and cosine math for stored embeddings (TEXT column, comma-separated floats). */
public final class EmbeddingVectors {

    private EmbeddingVectors() {
    }

    public static String serialize(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6g", vector[i]));
        }
        return sb.toString();
    }

    public static float[] deserialize(String stored) {
        String[] parts = stored.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }

    /** Cosine similarity clamped to {@code [0, 1]}; a zero-length vector scores 0 against everything. */
    public static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < n; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        for (int i = n; i < a.length; i++) {
            na += (double) a[i] * a[i];
        }
        for (int i = n; i < b.length; i++) {
            nb += (double) b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        double cosine = dot / (Math.sqrt(na) * Math.sqrt(nb));
        return Math.max(0.0, Math.min(1.0, cosine));
    }
}
