package integration

import dev.macmetaldemo.app.LockedRunMailbox
import dev.macmetaldemo.domain.*
import dev.macmetaldemo.application.*
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSThread
import kotlin.test.*

class RunMailboxTest {
    @Test fun progressDoesNotCreateRepeatedPhaseTransitions() {
        val mailbox = LockedRunMailbox()
        val running = RunSnapshot(RunPhase.RUNNING, null, null, RunProgress(0, 5, 0), 3, 0, null, null)
        mailbox.publish(running)
        assertEquals(listOf(running), mailbox.drainTransitions())
        mailbox.publish(running.copy(progress = RunProgress(1, 5, 1)))
        assertTrue(mailbox.drainTransitions().isEmpty())
        assertEquals(1L, mailbox.latest().progress?.completedUnits)
    }
    @Test fun transitionQueueHasAtMostOneEntryPerPhase() {
        val mailbox = LockedRunMailbox()
        val snapshot = RunSnapshot(RunPhase.PREPARING, null, null, null, null, null, null, null)
        repeat(100) {
            mailbox.publish(snapshot.copy(phase = RunPhase.PREPARING))
            mailbox.publish(snapshot.copy(phase = RunPhase.RUNNING))
        }
        assertEquals(listOf(RunPhase.PREPARING, RunPhase.RUNNING), mailbox.drainTransitions().map { it.phase })
    }

    @Test fun phaseAndOutcomeNotifyWaitingSession() {
        for (finish in listOf(false, true)) {
            val mailbox = LockedRunMailbox()
            val version = mailbox.changeVersion()
            val barrier = NSCondition()
            var ready = false
            var returned = false
            NSThread.detachNewThreadWithBlock {
                barrier.lock()
                ready = true
                barrier.broadcast()
                barrier.unlock()
                mailbox.waitForChange(version, 5_000_000_000L, WaitStrategy { fail("Fallback wait was used.") })
                barrier.lock()
                returned = true
                barrier.broadcast()
                barrier.unlock()
            }
            barrier.lock()
            val deadline = NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 2.0)
            while (!ready && barrier.waitUntilDate(deadline)) { }
            assertTrue(ready)
            barrier.unlock()
            if (finish) mailbox.finish(RunOutcome.Failure("done", false))
            else mailbox.publish(RunSnapshot(RunPhase.PREPARING, null, null, null, null, null, null, null))
            barrier.lock()
            while (!returned && barrier.waitUntilDate(deadline)) { }
            assertTrue(returned)
            barrier.unlock()
        }
    }

}
