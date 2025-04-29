package dev.boissin;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.boissin.controller.MetricsController;
import dev.boissin.model.FileItem;
import dev.boissin.queue.ParserQueue;
import dev.boissin.service.PhilosopherManager;
import dev.boissin.util.FileUtils;
import dev.boissin.util.WorkerContext;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final CountDownLatch LATCH = new CountDownLatch(1);

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help")) {
            printHelp();
            return;
        }

        String folderPath = null;
        boolean worker = false;

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-f":
                    case "--folder":
                        folderPath = args[++i];
                        break;
                    case "-w":
                    case "--worker":
                        worker = true;
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                }
            }

            final String appId;
            final PhilosopherManager dinner;
            final MetricsController metricsController;
            if (worker) {
                Thread.sleep(3000L);
                final WorkerContext context = WorkerContext.getContext();
                context.init(System.getenv("ZOOKEEPER_CONNECT"));
                appId = "worker-" + context.getWorkerId();

                dinner = new PhilosopherManager(3, 42L);
                dinner.launch();

                metricsController = new MetricsController((PrometheusMeterRegistry) context.getMeterRegistry());
            } else {
                appId = "client";
                dinner = null;
                metricsController = null;
            }

            if (folderPath == null && !worker) {
                throw new IllegalArgumentException("Folder path is required if process isn't a worker");
            }

            final ParserQueue parserQueue = new ParserQueue(worker, appId);
            parserQueue.init(System.getenv("ZOOKEEPER_CONNECT"));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    logger.info("Application {} is shutting down...", appId);
                    parserQueue.close();
                    if (dinner != null) {
                        dinner.close();
                    }
                    if (metricsController != null) {
                        metricsController.close();
                    }
                    WorkerContext.getContext().close();
                    LATCH.countDown();
                } catch (IOException ioe) {
                    logger.error("Error when stopping parser queue", ioe);
                }
            }));

            if (!worker) {
                logger.info("Send file items with folder path: {}", folderPath);
                final List<FileItem> fileItems = FileUtils.generateFileItems(folderPath);
                for (FileItem fileItem: fileItems) {
                    parserQueue.sendFileItem(fileItem);
                }
            } else {
                logger.info("Starting as worker with ID: {}", appId);
                logger.info("Worker is running. Send SIGTERM to gracefully shut down.");
                try {
                    LATCH.await();
                } catch (InterruptedException e) {
                    logger.warn("Worker was interrupted while waiting");
                }
                logger.info("Worker has completed shutdown process");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            logger.error("Error in main " + e.getMessage(), e);
            printHelp();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar app.jar [options]");
        System.out.println("Options:");
        System.out.println("  -f, --folder <file path>   File path (required if not worker)");
        System.out.println("  -w, --worker               Enable worker mode");
    }

}
