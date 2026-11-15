package dev.macmetaldemo.app

import dev.macmetaldemo.application.*
import dev.macmetaldemo.domain.RunRequest
import platform.Foundation.NSThread

class NativeRunWorker(private val controller: RunController) : RunWorker {
    override fun start(request: RunRequest): RunHandle = start(request, false)
    override fun startObserved(request: RunRequest): RunHandle = start(request, true)

    private fun start(request: RunRequest, requireContext: Boolean): RunHandle {
        val mailbox = LockedRunMailbox(requireContext, NativeVisualizationExchange(request.visualizationEnabled))
        NSThread.detachNewThreadWithBlock {
            val outcome = try { controller.run(request, mailbox.visualization, mailbox) } catch (error: Throwable) {
                RunOutcome.Failure(error.message ?: "Run failed.", true)
            }
            mailbox.finish(outcome)
        }
        return mailbox
    }
}
