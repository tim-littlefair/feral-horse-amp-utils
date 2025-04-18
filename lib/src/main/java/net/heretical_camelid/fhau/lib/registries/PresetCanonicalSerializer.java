package net.heretical_camelid.fhau.lib.registries;

import com.google.gson.Gson;
import com.google.gson.annotations.Since;

import java.util.Arrays;

/**
 * This class and its static inner classes are constructed to satisfy
 * the recommendations described in
 * https://google.github.io/gson/UserGuide.html#primitives-examples
 * for a class to be suitable for bidirectional serialization to and
 * from JSON using the GSON framework.
 * The primary use of this class is to standardize the order of
 * fields when the preset or a subtree of it is serialized for
 * the purpose of comparing preset content or generating a hash.
 * The order of members exactly matches the order in the JSON
 * retrieved from an LT40S running firmware version 1.0.7 for
 * preset #1 "FENDER  CLEAN   ".
 */
public class PresetCanonicalSerializer {
    static Gson s_gsonCompact = new Gson();

    String nodeType;
    String NodeId;
    String version;
    int numInputs;
    int numOutputs;
    PCS_Info info;
    PCS_AudioGraph audioGraph;
    public PresetCanonicalSerializer() {}
    public void makeCanonical() {
        // The two arrays have inconsistent order when the same preset
        // is copied to a different slot (or maybe when it is edited
        // in Fender Tone, even if the final state is equal to the
        // original state).
        // Sorting the arrays resolves the inconsistency and helps to
        // reduce the number of presets with the same name and
        // identical parameters but different hashes due to JSON
        // element ordering
        Arrays.sort(audioGraph.connections);
        Arrays.sort(audioGraph.nodes);
    }

    static class PCS_AudioGraph {
        PCS_Node[] nodes;
        PCS_Connection[] connections;
    }

    static class PCS_Info {
        String displayName;
        String preset_id;
        String author;
        String source_id;
        int timestamp;
        int created_at;
        String productId;
        boolean is_factory_default = true;
        int bpm;
        PCS_Info() { }
    }

    static class PCS_Node implements Comparable<PCS_Node> {
        String nodeId;
        String dspUnit;
        String FenderId;
        PCS_DspUnitParameters dspUnitParameters;
        PCS_Node() {}
        public int compareTo(PCS_Node other) {
            return s_gsonCompact.toJson(this).compareTo(s_gsonCompact.toJson(other));
        }
    }

    static class PCS_DspUnitParameters {
        // scripts/extract_dsp_param_domains.py
        // was used to scan all of the compact
        // JSON retrieved from the LT40S and
        // generate an alphabetic list of the
        // attributes which appear in this item
        // with their domains.

        // This list is in alphabetical order with
        // a small number of exceptions which require
        // comments and/or annotations, these appear
        // at the end
        Float attenuate;
        Float attenuation;
        Float avgDelay;
        Float bass;
        Float bias;
        Float blend;
        Boolean bright;
        Float brite;
        // bypass: at end of list
        // bypassType: at end of list
        String cabsimType;
        Float chase;
        Float cut;
        Float decay;
        Float depth;
        Float diffuse;
        Float dist;
        Float dlyTime;
        Float duty;
        Float dwell;
        Float feedback;
        Float gain;
        Float gain2;
        String gateDetectorPosition;
        String gatePreset;
        Float hiFrq;
        Float high;
        Float highmid;
        Float hysteresis;
        Float level;
        Float loFrq;
        Float low;
        Float lowmid;
        Float lrPhase;
        Float master;
        Float mid;
        String mode;
        String noteDivision;
        Integer octdown;
        Integer octup;
        Float outputLevel;
        Float phase;
        Float presence;
        Float q;
        Float rate;
        Float rateHz;
        Float reson;
        Float rotor;
        String sag;
        Float sensitivity;
        // shape: at end of list
        Float stereoSpread;
        Float tapTimeBPM;
        Float thresh;
        Float threshold;
        Float time;
        // tone: at end of list
        Float treb;
        Float treble;
        String type;
        Float volume;
        Float wetLvl;
        Float wowLevel;

        // The remaining attributes have been identified
        // as taking on inconsistent values and/or types when a preset
        // is copied, or edited or imported using Fender Tone
        // The @Since annotation is used on these so that
        // they can be excluded from the JSON used to calculate
        // a hash.

        // bypass switches between False and absent
        @Since(99.99)
        Boolean bypass;

        // bypassType switches between 'Post', 'Pre' and absent
        @Since(99.99)
        String bypassType;

        // tone switches between 'normal' and (numeric) 0.5
        @Since(99.99)
        String tone;

        // shape switches between 'sine' and (numeric) 0
        @Since(99.99)
        String shape;

        PCS_DspUnitParameters() { }
    }

    static class PCS_Connection implements Comparable<PCS_Connection> {
        PCS_Connection_IO input;
        PCS_Connection_IO output;
        PCS_Connection() { }
        public int compareTo(PCS_Connection other) {
            return s_gsonCompact.toJson(this).compareTo(s_gsonCompact.toJson(other));
        }
    }

    static class PCS_Connection_IO {
        String nodeId;
        int index;
        PCS_Connection_IO() {}
    }
}
