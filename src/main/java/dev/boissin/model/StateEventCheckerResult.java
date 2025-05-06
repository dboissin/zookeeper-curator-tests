/**
 * Dining Philosophers - Zookeeper/Curator Case Study
 * Copyright (C) 2025 Damien BOISSIN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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
