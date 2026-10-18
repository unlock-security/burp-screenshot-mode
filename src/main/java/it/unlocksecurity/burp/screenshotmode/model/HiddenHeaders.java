package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;

public final class HiddenHeaders {

    public List<String> both = new ArrayList<>();
    public List<String> request = new ArrayList<>();
    public List<String> response = new ArrayList<>();

    public List<String> get(HeaderHideTarget target) {
        return switch (target) {
            case BOTH -> both;
            case REQUEST -> request;
            case RESPONSE -> response;
        };
    }

    public void set(HeaderHideTarget target, List<String> headers) {
        List<String> copy = new ArrayList<>(headers);
        switch (target) {
            case BOTH -> both = copy;
            case REQUEST -> request = copy;
            case RESPONSE -> response = copy;
        }
    }

    /** Rules applying to the given message: the shared ones followed by the target-specific ones. */
    public List<String> rulesFor(RuleTarget target) {
        List<String> rules = new ArrayList<>(both);
        rules.addAll(target == RuleTarget.REQUEST ? request : response);
        return rules;
    }

    public HiddenHeaders copy() {
        HiddenHeaders copy = new HiddenHeaders();
        copy.both = new ArrayList<>(both);
        copy.request = new ArrayList<>(request);
        copy.response = new ArrayList<>(response);
        return copy;
    }
}
