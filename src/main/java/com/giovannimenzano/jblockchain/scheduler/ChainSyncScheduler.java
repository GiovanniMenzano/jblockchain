package com.giovannimenzano.jblockchain.scheduler;

import com.giovannimenzano.jblockchain.services.INetworkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChainSyncScheduler {

	private final INetworkService networkService;

	@Value("${blockchain.sync.enabled:true}")
	private boolean syncEnabled;

	/**
	 * Runs immediately when the node is fully started.
	 * Ensures the node is up-to-date with the rest of the network
	 * even after a restart or downtime.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		if (!syncEnabled) {
			return;
		}
		log.info("[Scheduler] Node online - performing initial chain sync with peers...");
		boolean replaced = networkService.resolveConflicts();
		if (replaced) {
			log.info("[Scheduler] Initial sync complete - chain updated from peer.");
		} else {
			log.info("[Scheduler] Initial sync complete - local chain is up to date.");
		}
	}

	/**
	 * Periodic consensus round. Handles the case where a node missed a broadcast
	 * due to temporary downtime or a transient network error.
	 * Interval is configurable via blockchain.sync.interval (default: 30 seconds).
	 */
	@Scheduled(fixedRateString = "${blockchain.sync.interval:30000}")
	public void periodicSync() {
		if (!syncEnabled || networkService.getNodes().isEmpty()) {
			return;
		}
		log.debug("[Scheduler] Periodic sync - checking peers for longer chain...");
		boolean replaced = networkService.resolveConflicts();
		if (replaced) {
			log.info("[Scheduler] Periodic sync - chain updated from peer.");
		}
	}
}
