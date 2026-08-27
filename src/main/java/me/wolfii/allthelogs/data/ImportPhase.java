package me.wolfii.allthelogs.data;

/**
 * Stages of one import run. File discovery and writing occupy the first share of the progress bar;
 * rewriting {@code chat_entry} into time-ordered row groups and compacting the database file occupy
 * the rest. Live session capture does not use these phases.
 */
public enum ImportPhase {
    IMPORT,
    CHUNKING,
    OPTIMIZING
}
