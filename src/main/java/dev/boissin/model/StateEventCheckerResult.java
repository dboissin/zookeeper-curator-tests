package dev.boissin.model;

import java.util.List;

public class StateEventCheckerResult {

    private List<Event> verifiedEvents;
    private List<Event> errorEvents;

    public List<Event> getVerifiedEvents() {
        return verifiedEvents;
    }

    public List<Event> getErrorEvents() {
        return errorEvents;
    }

    public void setVerifiedEvents(List<Event> verifiedEvents) {
        this.verifiedEvents = verifiedEvents;
    }

    public void setErrorEvents(List<Event> errorEvents) {
        this.errorEvents = errorEvents;
    }

}
