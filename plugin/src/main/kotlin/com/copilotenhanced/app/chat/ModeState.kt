package com.copilotenhanced.app.chat

enum class ModeState {
    IDLE,
    CLARIFYING,
    PLAN_READY,
    REVISING,
    APPROVED,
    BUILDING,
    VERIFYING,
    DONE,
    REJECTED;

    val isBuildPhase: Boolean
        get() = when (this) {
            APPROVED, BUILDING, VERIFYING, DONE -> true
            IDLE, CLARIFYING, PLAN_READY, REVISING, REJECTED -> false
        }
}
