package org.shubh.assignment.service;

public enum InteractionType {
    BOT_REPLY(1),
    HUMAN_LIKE(20),
    HUMAN_COMMENT(50);

    private final long scoreDelta;

    InteractionType(long scoreDelta) {
        this.scoreDelta = scoreDelta;
    }

    public long scoreDelta() {
        return scoreDelta;
    }
}
