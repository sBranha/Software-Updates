package com.newswire.vehicleporttester;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Byte-level heuristics only. This parser never sends data or interprets
 * encrypted application content as confirmed Ford diagnostic information.
 */
final class UsbProtocolAnalyzer {
    private UsbProtocolAnalyzer() {}

    static JSONObject analyze(byte[] data, int count) throws Exception {
        int n = Math.max(0, Math.min(count, data == null ? 0 : data.length));
        JSONObject out = new JSONObject();
        out.put("analyzed_bytes", n);
        out.put("interpretation", "Heuristic header matching only; does not establish Ford SYNC, OBD-II, or Android Auto message meaning.");
        out.put("vehicle_data_confirmed", false);
        int[] frequency = new int[256];
        for (int i = 0; i < n; i++) frequency[data[i] & 255]++;
        int distinct = 0;
        double entropy = 0;
        if (n > 0) {
            for (int hits : frequency) {
                if (hits == 0) continue;
                distinct++;
                double p = (double) hits / n;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        out.put("distinct_byte_values", distinct);
        out.put("shannon_entropy_bits_per_byte", Math.round(entropy * 1000) / 1000.0);
        out.put("entropy_note", "Entropy is descriptive only; higher values do not prove encryption.");

        JSONArray frameList = new JSONArray();
        int candidates = 0;
        int complete = 0;
        int incomplete = 0;
        int tlsLike = 0;
        int largest = 0;
        int smallest = Integer.MAX_VALUE;
        int tlsApplicationData = 0;
        int tlsHandshake = 0;

        // The previous Explorer capture showed 06 0B followed by a
        // big-endian length, then a TLS-like 5-byte header. Check lengths
        // to avoid labeling a random 03 03 sequence as a real record.
        for (int i = 0; i + 8 < n; i++) {
            if ((data[i] & 255) != 0x06 || (data[i + 1] & 255) != 0x0B) continue;
            int outer = u16(data[i + 2], data[i + 3]);
            int recordType = data[i + 4] & 255;
            int major = data[i + 5] & 255;
            int minor = data[i + 6] & 255;
            int recordBytes = u16(data[i + 7], data[i + 8]);
            if (major != 3 || minor > 4 || recordType < 20 || recordType > 23) continue;
            if (outer != recordBytes + 5 || outer > 16400 || outer < 5) continue;
            candidates++;
            largest = Math.max(largest, outer);
            smallest = Math.min(smallest, outer);
            boolean wholeFrame = (i + 4 + outer) <= n;
            if (wholeFrame) complete++; else incomplete++;
            tlsLike++;
            if (recordType == 23) tlsApplicationData++;
            if (recordType == 22) tlsHandshake++;
            if (frameList.length() < 100) {
                JSONObject f = new JSONObject();
                f.put("offset", i);
                f.put("outer_prefix_hex", "06 0B");
                f.put("outer_payload_length", outer);
                f.put("inner_record_type", recordType);
                f.put("inner_version_hex", String.format(java.util.Locale.US, "%02X %02X", major, minor));
                f.put("inner_payload_length", recordBytes);
                f.put("length_fields_agree", true);
                f.put("complete_in_capture", wholeFrame);
                frameList.put(f);
            }
            // Skip the validated frame interior; avoids finding signatures in ciphertext.
            if (wholeFrame) i += outer + 3;
        }
        JSONObject framing = new JSONObject();
        framing.put("pattern", "06 0B + 16-bit big-endian length + TLS-like record header");
        framing.put("candidate_frames", candidates);
        framing.put("complete_frames", complete);
        framing.put("truncated_frames", incomplete);
        framing.put("minimum_payload_bytes", candidates == 0 ? JSONObject.NULL : smallest);
        framing.put("maximum_payload_bytes", largest);
        framing.put("tls_like_record_headers", tlsLike);
        framing.put("tls_application_data_type_23", tlsApplicationData);
        framing.put("tls_handshake_type_22", tlsHandshake);
        framing.put("frames", frameList);
        framing.put("confidence", candidates >= 2 ? "multiple_consistent_header_matches" :
                candidates == 1 ? "single_header_match" : "no_matching_headers");
        framing.put("warning", "Matching TLS-like record headers does not identify the source, decrypt data, or confirm access to Ford SYNC.");
        out.put("framing_analysis", framing);
        return out;
    }

    private static int u16(byte hi, byte lo) {
        return ((hi & 255) << 8) | (lo & 255);
    }
}
