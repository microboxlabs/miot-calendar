package com.microboxlabs.miot.calendar.service;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Inner Quarkus scheduler for automatic slot generation.
 *
 * Execution modes:
 *
 *  1. Default (single instance):
 *     Uses ConcurrentExecution.SKIP to prevent overlap within the same
 *     JVM, plus a DB-based soft lock in SlotManagerExecutor for basic
 *     protection across restarts.
 *
 *  2. HA / multi-instance:
 *     Add quarkus-quartz to the classpath and configure:
 *       quarkus.quartz.store-type=jdbc-cmt
 *       quarkus.quartz.clustered=true
 *     Quartz will transparently back this @Scheduled method and
 *     guarantee only one node runs per trigger across the cluster.
 *
 *  3. Kubernetes CronJob (external trigger):
 *     Set miot-calendar.slot-manager.cron=off to disable this scheduler
 *     and call POST /api/v1/miot-calendar/slot-managers/run from a
 *     K8s CronJob, or launch the jar with the "run-slots" CLI argument.
 *
 * Cron default: top of every hour.
 * Override: miot-calendar.slot-manager.cron=0 30 * * * ?
 * Disable:  miot-calendar.slot-manager.cron=off
 */
@ApplicationScoped
public class SlotManagerScheduler {

    private static final Logger LOG = Logger.getLogger(SlotManagerScheduler.class);

    private final SlotManagerExecutor executor;

    @Inject
    public SlotManagerScheduler(SlotManagerExecutor executor) {
        this.executor = executor;
    }

    @Scheduled(
        cron              = "${miot-calendar.slot-manager.cron:0 0 * * * ?}",
        identity          = "slot-manager",
        concurrentExecution = ConcurrentExecution.SKIP
    )
    void run() {
        LOG.debug("Slot manager scheduler triggered");
        executor.runAll("SCHEDULER");
    }
}
