package com.dexstudios.dex.core.network.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PunchSessionTest {

    @Test
    fun session_state_transitions_correctly_from_Init_to_Punching_to_Connected() {
        val session = PunchSession("peer1")
        assertEquals(PunchSession.State.INIT, session.state)

        session.transition(PunchSession.State.PUNCHING)
        assertEquals(PunchSession.State.PUNCHING, session.state)

        session.transition(PunchSession.State.CONNECTED)
        assertEquals(PunchSession.State.CONNECTED, session.state)
    }

    @Test
    fun session_fails_transition_if_invalid_state() {
        val session = PunchSession("peer1")

        assertFailsWith<IllegalStateException> {
            session.transition(PunchSession.State.FAILED)
            session.transition(PunchSession.State.CONNECTED)
        }
    }

    @Test
    fun session_records_ping_timestamps_and_computes_latency() {
        val session = PunchSession("peer2")
        session.recordPingSent(100L)
        session.recordPongReceived(150L)

        val latency = session.calculateLatency()
        assertEquals(50L, latency)
    }

    @Test
    fun session_fails_to_compute_latency_if_pong_not_received() {
        val session = PunchSession("peer3")
        session.recordPingSent(100L)

        val latency = session.calculateLatency()
        assertEquals(-1L, latency)
    }
}
